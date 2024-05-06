package de.andrenitze.softpro.types;

import de.andrenitze.softpro.entities.GameOverStats;

import javax.sql.DataSource;
import java.sql.*;

import static de.andrenitze.softpro.Main.logger;

public class GameOverStatsDAO {
    private final DataSource dataSource;

    public GameOverStatsDAO(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public boolean saveGameOverStats(GameOverStats goStats) {
        String sql = "INSERT INTO GameOverStats (deliveredProjects, projectsVolume, report, playerName, ipAddress, finishedAt, gameId, survivedDays, playedSeconds) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, goStats.getDeliveredProjects());
            stmt.setInt(2, goStats.getProjectsVolume());
            stmt.setString(3, goStats.getReport());
            stmt.setString(4, goStats.getPlayerName());
            stmt.setString(5, goStats.getIpAddress());
            stmt.setTimestamp(6, new Timestamp(goStats.getFinishedAt().getTime()));
            stmt.setString(7, goStats.getGameId());
            stmt.setInt(8, goStats.getSurvivedDays());
            stmt.setInt(9, goStats.getPlayedSeconds());

            int affectedRows = stmt.executeUpdate();
            return affectedRows > 0;
        } catch (SQLException e) {
            logger.error("Failed to save game stats for game {}: {}", goStats.getGameId(), e.getMessage(), e);
            return false;
        }
    }

    public GameOverStats getCurrentHighScore() {
        String sql = "SELECT * FROM GameOverStats WHERE DATE(finishedAt) = CURRENT_DATE ORDER BY projectsVolume DESC LIMIT 1";
        GameOverStats highScore = null;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                highScore = new GameOverStats();
                highScore.setId(rs.getInt("id"));
                highScore.setDeliveredProjects(rs.getInt("deliveredProjects"));
                highScore.setProjectsVolume(rs.getInt("projectsVolume"));
                highScore.setReport(rs.getString("report"));
                highScore.setPlayerName(rs.getString("playerName"));
                highScore.setIpAddress(rs.getString("ipAddress"));
                highScore.setFinishedAt(new Timestamp(rs.getTimestamp("finishedAt").getTime()));
                highScore.setGameId(rs.getString("gameId"));
                highScore.setSurvivedDays(rs.getInt("survivedDays"));
                highScore.setPlayedSeconds(rs.getInt("playedSeconds"));
            }
        } catch (SQLException e) {
            logger.error("Could not fetch high-score from database: {}", e.getMessage());
            return null;
        }
        return highScore;
    }
}
