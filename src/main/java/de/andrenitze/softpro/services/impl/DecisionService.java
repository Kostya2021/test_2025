package de.andrenitze.softpro.services.impl;

import de.andrenitze.softpro.domains.decisions.Decision;
import de.andrenitze.softpro.domains.decisions.DecisionDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DecisionService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DecisionService.class);
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
