package de.andrenitze.softpro.services.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import de.andrenitze.softpro.domains.decisions.Decision;
import de.andrenitze.softpro.domains.decisions.DecisionEntry;
import de.andrenitze.softpro.domains.decisions.PlayerDecision;
import de.andrenitze.softpro.repositories.PlayerDecisionRepository;
import java.util.List;

@Slf4j
@Service
public class DecisionService {

    private final PlayerDecisionRepository repository;

    public DecisionService(PlayerDecisionRepository repository) {
        this.repository = repository;
    }

    @Async
    public void saveDecisionsAsync(String userId, int level, List<Decision> decisions) {
        PlayerDecision pd = new PlayerDecision();
        pd.setUserId(userId);
        pd.setLevel(level);

        List<DecisionEntry> entries = decisions.stream().map(d -> {
            DecisionEntry e = new DecisionEntry();
            e.setDecisionId(d.getDecisionId());
            e.setOptionId(d.getOptionId());
            e.setPlayerDecision(pd);
            return e;
        }).toList();

        pd.setDecisions(entries);
        repository.save(pd);
        log.debug("Saved {} decisions for user {} at level {}", entries.size(), userId, level);
    }

    public List<PlayerDecision> loadDecisions(String userId) {
        return repository.findByUserId(userId);
    }
}
