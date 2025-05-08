package de.andrenitze.softpro.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableAsync;

import de.andrenitze.softpro.domains.decisions.DecisionDAO;
import de.andrenitze.softpro.types.GameOverStatsDAO;

@Configuration
@EnableAsync
public class DaoConfig {

    @Bean
    public DecisionDAO decisionDAO(JdbcTemplate jdbcTemplate) {
        return new DecisionDAO(jdbcTemplate);
    }

    @Bean
    public GameOverStatsDAO gameOverStatsDAO(JdbcTemplate jdbcTemplate) {
        return new GameOverStatsDAO(jdbcTemplate);
    }
}