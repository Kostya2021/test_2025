package de.andrenitze.softpro.services;

import de.andrenitze.softpro.domains.decisions.Decision;
import de.andrenitze.softpro.domains.decisions.DecisionDAO;
import de.andrenitze.softpro.config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DecisionService {
    private static final Logger logger = LoggerFactory.getLogger(DecisionService.class);
    private final DecisionDAO decisionDao;
    private final ExecutorService executorService;

    public DecisionService() {
        this.decisionDao = new DecisionDAO(DatabaseConfig.getDataSource());
        this.executorService = Executors.newSingleThreadExecutor();
    }

    public void saveDecisionsAsync(String playerId, int level, List<Decision> decisions) {
        executorService.submit(() -> {
            try {
                decisionDao.saveDecisions(playerId, level, decisions);
            } catch (SQLException e) {
                logger.error("Could not persist player decisions to database: {}", e.getMessage());
            }
        });
    }
}