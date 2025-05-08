package de.andrenitze.softpro;

import de.andrenitze.softpro.domains.decisions.DecisionDAO;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class TestDaoConfig {

    @Bean
    public JdbcTemplate jdbcTemplate() {
        return Mockito.mock(JdbcTemplate.class);
    }

    @Bean
    public DecisionDAO decisionDAO(JdbcTemplate jdbcTemplate) {
        return new DecisionDAO(jdbcTemplate);
    }
}