package de.andrenitze.softpro.domains.employees;

import de.andrenitze.softpro.Game;
import de.andrenitze.softpro.MessagingService;
import de.andrenitze.softpro.ProjectService;
import lombok.Setter;

import static de.andrenitze.softpro.Main.logger;

public class EmployeeService {
    private final Game game;
    private final ProjectService projectService;
    @Setter
    private MessagingService messagingService;

    public EmployeeService(Game game, ProjectService projectService) {
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
