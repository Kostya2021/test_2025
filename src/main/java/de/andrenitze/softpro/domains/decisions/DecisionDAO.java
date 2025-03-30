package de.andrenitze.softpro.domains.decisions;

import javax.sql.DataSource;
import java.net.ConnectException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DecisionDAO {
    private final DataSource dataSource;

    public DecisionDAO(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void saveDecisions(String playerId, int level, List<Decision> decisions) throws SQLException {
        String sql = "INSERT INTO Decisions (decisionId, level, selectedOption, playerId) VALUES (?, ?, ?, ?)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(2, level);
            stmt.setString(4, playerId);
            for (Decision decision : decisions) {
                stmt.setInt(1, decision.getDecisionId());
                stmt.setInt(3, decision.getOptionId());
                stmt.addBatch();
            }
            stmt.executeBatch();
        } catch (SQLException e) {
            throw new SQLException("Error while saving decisions", e);
        }
    }

    public Map<Integer, List<OptionVoteDistribution>> getVoteDistributionByLevel(int level) throws ConnectException {
        Map<Integer, List<OptionVoteDistribution>> groupedDistributions = new HashMap<>();
        String sql = "SELECT" +
                " decisionId," +
                " selectedOption AS 'option'," +
                " COUNT(*) AS vote_count," +
                " COUNT(*) * 100.0 / SUM(COUNT(*)) OVER (PARTITION BY decisionId) AS percentage " +
                "FROM" +
                " Decisions " +
                "WHERE" +
                " level = ? " +
                "GROUP BY" +
                " decisionId, selectedOption;";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, level);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int decisionId = rs.getInt("decisionId");
                    OptionVoteDistribution distribution = new OptionVoteDistribution(
                            decisionId,
                            rs.getInt("option"),
                            rs.getInt("vote_count"),
                            rs.getDouble("percentage")
                    );
                    groupedDistributions.computeIfAbsent(decisionId, _ -> new ArrayList<>()).add(distribution);
                }
            }
        } catch (SQLException e) {
            throw new ConnectException("Error while fetching vote distribution");
        }

        return groupedDistributions;
    }
}