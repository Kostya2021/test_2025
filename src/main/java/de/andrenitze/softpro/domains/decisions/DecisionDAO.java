package de.andrenitze.softpro.domains.decisions;

import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DecisionDAO {

    private final JdbcTemplate jdbcTemplate;

    public DecisionDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void saveDecisions(String playerId, int level, List<Decision> decisions) {
        String sql = "INSERT INTO Decisions (decisionId, level, selectedOption, playerId) VALUES (?, ?, ?, ?)";

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement stmt, int i) throws SQLException {
                Decision decision = decisions.get(i);
                stmt.setInt(1, decision.getDecisionId());
                stmt.setInt(2, level);
                stmt.setInt(3, decision.getOptionId());
                stmt.setString(4, playerId);
            }

            @Override
            public int getBatchSize() {
                return decisions.size();
            }
        });
    }

    public Map<Integer, List<OptionVoteDistribution>> getVoteDistributionByLevel(int level) {
        String sql = "SELECT decisionId, selectedOption AS option, COUNT(*) AS vote_count, " +
                "COUNT(*) * 100.0 / SUM(COUNT(*)) OVER (PARTITION BY decisionId) AS percentage " +
                "FROM Decisions WHERE level = ? GROUP BY decisionId, selectedOption";

        List<OptionVoteDistribution> distributions = jdbcTemplate.query(sql, new Object[]{level}, (rs, rowNum) -> {
            return new OptionVoteDistribution(
                    rs.getInt("decisionId"),
                    rs.getInt("option"),
                    rs.getInt("vote_count"),
                    rs.getDouble("percentage")
            );
        });

        // Gruppieren nach decisionId
        Map<Integer, List<OptionVoteDistribution>> groupedDistributions = new HashMap<>();
        for (OptionVoteDistribution dist : distributions) {
            groupedDistributions
                    .computeIfAbsent(dist.getDecisionId(), k -> new ArrayList<>())
                    .add(dist);
        }

        return groupedDistributions;
    }
}
