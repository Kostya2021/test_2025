package de.andrenitze.softpro.domains.decisions;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class DecisionDAO {

    private final JdbcTemplate jdbcTemplate;

    public DecisionDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<Integer, List<OptionVoteDistribution>> getVoteDistributionByLevel(int level) {
        String sql = """
            SELECT 
                d.decision_id,
                d.option_id AS option,
                COUNT(*) AS vote_count,
                COUNT(*) * 100.0 / SUM(COUNT(*)) OVER (PARTITION BY d.decision_id) AS percentage
            FROM decision_entry d
            JOIN player_decision pd ON d.player_decision_id = pd.id
            WHERE pd.level = ?
            GROUP BY d.decision_id, d.option_id
            """;

        List<OptionVoteDistribution> distributions = jdbcTemplate.query(sql, new Object[]{level}, (rs, rowNum) -> new OptionVoteDistribution(
                rs.getInt("decision_id"),
                rs.getInt("option"),
                rs.getInt("vote_count"),
                rs.getDouble("percentage")
        ));

        // Group by decision_id
        Map<Integer, List<OptionVoteDistribution>> groupedDistributions = new HashMap<>();
        for (OptionVoteDistribution dist : distributions) {
            groupedDistributions
                    .computeIfAbsent(dist.getDecisionId(), k -> new ArrayList<>())
                    .add(dist);
        }

        return groupedDistributions;
    }
}
