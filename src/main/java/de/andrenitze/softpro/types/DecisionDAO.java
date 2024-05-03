package de.andrenitze.softpro.types;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

public class DecisionDAO {
    private final DataSource dataSource;

    public DecisionDAO(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void saveDecisions(String playerId, int level, List<Decision> decisions) throws SQLException {
        String sql = "INSERT INTO Decisions (id, level, selected_option, playerId) VALUES (?, ?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (Decision decision : decisions) {
                stmt.setInt(1, decision.getDecisionId());
                stmt.setInt(2, level);
                stmt.setInt(3, decision.getOptionId());
                stmt.setString(4, playerId);
                stmt.executeUpdate();
            }
        }
    }
}