package de.andrenitze.softpro.repositories;

import de.andrenitze.softpro.domains.decisions.PlayerDecision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlayerDecisionRepository extends JpaRepository<PlayerDecision, Long> {
    List<PlayerDecision> findByUserId(String userId);
    List<PlayerDecision> findByUserIdAndLevel(String userId, int level);
}
