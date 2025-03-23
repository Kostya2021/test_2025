package de.andrenitze.softpro.services.impl;

import de.andrenitze.softpro.Game;
import de.andrenitze.softpro.services.EmployeeService;
import de.andrenitze.softpro.services.MessagingService;
import lombok.Setter;

import static de.andrenitze.softpro.Main.logger;

public class EmployeeServiceImpl implements EmployeeService {
    private final Game game;
    private final ProjectServiceImpl projectService;
    @Setter
    private MessagingService messagingService;

    public EmployeeServiceImpl(Game game, ProjectServiceImpl projectService) {
        this.game = game;
        this.projectService = projectService;
        this.messagingService = null;
        logger.debug("EmployeeService initialized.");
    }

    public void simulateEmployeeLives() {
        game.getPlayerService().getPlayers().forEach((_, player) -> player.getEmployees().forEach(employee -> {
            employee.liveLife(game.getCurrentTick());
            boolean needsUpdate = employee.isSick() || employee.hasFirstDayAfterSickLeave(game.getCurrentTick()) || employee.removeExpiredStatusEffects();

            // Annual events that affect employees
            if (game.getCurrentTick() % 365 == 0) {
                employee.initializeSickDays();
            }

            // Monthly events that affect employees
            if (game.getCurrentTick() % 30 == 0) {
                // Send at least one update per month for metrics (i.e., utilization, sick days, satisfaction)
                needsUpdate = true;
            }

            // This could be refactored so that the "needsUpdate" logic can be used here as well
            projectService.applyStatusEffectsForStressfulOnboarding(player, employee);

            // Send an employee update, if anything has changed
            if (needsUpdate) {
                messagingService.sendEmployeeUpdate(player, employee);
            }
        }));
    }

}
