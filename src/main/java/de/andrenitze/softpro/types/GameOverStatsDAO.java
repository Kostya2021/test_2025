package de.andrenitze.softpro.types;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import de.andrenitze.softpro.domains.GameOverStats;
import java.sql.Timestamp;
import java.util.List;

@Slf4j
public class GameOverStatsDAO {
    private final JdbcTemplate jdbcTemplate;

    public GameOverStatsDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean saveGameOverStats(GameOverStats goStats) {
        String sql = "INSERT INTO GameOverStats " +
                "(deliveredProjects, projectsVolume, report, playerName, ipAddress, finishedAt, gameId, survivedDays, playedSeconds) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try {
            int affectedRows = jdbcTemplate.update(sql, ps -> {
                ps.setInt(1, goStats.getDeliveredProjects());
                ps.setInt(2, goStats.getProjectsVolume());
                ps.setString(3, goStats.getReport());
                ps.setString(4, goStats.getPlayerName());
                ps.setString(5, goStats.getIpAddress());
                ps.setTimestamp(6, new Timestamp(goStats.getFinishedAt().getTime()));
                ps.setString(7, goStats.getGameId());
                ps.setInt(8, goStats.getSurvivedDays());
                ps.setInt(9, goStats.getPlayedSeconds());
            });
            return affectedRows > 0;
        } catch (Exception e) {
            log.error("Failed to save game stats for game {}: {}", goStats.getGameId(), e.getMessage(), e);
            return false;
        }
    }

    public List<GameOverStats> getCurrentHighScores() {
        String sql = "(SELECT 'daily' AS period, id, deliveredProjects, projectsVolume, report, playerName, ipAddress, finishedAt, gameId, survivedDays, playedSeconds " +
                "FROM GameOverStats WHERE DATE(finishedAt) = CURRENT_DATE ORDER BY projectsVolume DESC LIMIT 3) " +
                "UNION ALL " +
                "(SELECT 'monthly' AS period, id, deliveredProjects, projectsVolume, report, playerName, ipAddress, finishedAt, gameId, survivedDays, playedSeconds " +
                "FROM GameOverStats WHERE YEAR(finishedAt) = YEAR(CURRENT_DATE) AND MONTH(finishedAt) = MONTH(CURRENT_DATE) ORDER BY projectsVolume DESC LIMIT 3) " +
                "UNION ALL " +
                "(SELECT 'quarterly' AS period, id, deliveredProjects, projectsVolume, report, playerName, ipAddress, finishedAt, gameId, survivedDays, playedSeconds " +
                "FROM GameOverStats WHERE YEAR(finishedAt) = YEAR(CURRENT_DATE) AND QUARTER(finishedAt) = QUARTER(CURRENT_DATE) ORDER BY projectsVolume DESC LIMIT 3)";

        try {
            return jdbcTemplate.query(sql, gameOverStatsRowMapper);
        } catch (Exception e) {
            log.error("Could not fetch high-score from database: {}", e.getMessage(), e);
            return List.of();
        }
    }

    private final RowMapper<GameOverStats> gameOverStatsRowMapper = (rs, rowNum) -> {
        GameOverStats highScore = new GameOverStats();
        highScore.setPeriod(rs.getString("period"));
        highScore.setId(rs.getInt("id"));
        highScore.setDeliveredProjects(rs.getInt("deliveredProjects"));
        highScore.setProjectsVolume(rs.getInt("projectsVolume"));
        highScore.setReport(rs.getString("report"));
        highScore.setPlayerName(rs.getString("playerName"));
        highScore.setIpAddress(rs.getString("ipAddress"));
        highScore.setFinishedAt(rs.getTimestamp("finishedAt"));
        highScore.setGameId(rs.getString("gameId"));
        highScore.setSurvivedDays(rs.getInt("survivedDays"));
        highScore.setPlayedSeconds(rs.getInt("playedSeconds"));
        return highScore;
    };
}
