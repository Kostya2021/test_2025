package de.andrenitze.softpro.services.impl;

import de.andrenitze.softpro.domains.decisions.Decision;
import de.andrenitze.softpro.domains.decisions.DecisionDAO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class DecisionService {
    private final DecisionDAO decisionDao;

    @Autowired
    public DecisionService(DecisionDAO decisionDao) {
        this.decisionDao = decisionDao;
    }

    @Async
    public void saveDecisionsAsync(String playerId, int level, List<Decision> decisions) {
        try {
            decisionDao.saveDecisions(playerId, level, decisions);
        } catch (Exception e) {
            log.error("Could not persist player decisions to database: {}", e.getMessage());
        }
    }
}
