package de.andrenitze.softpro.services.impl;

import de.andrenitze.softpro.Game;
import de.andrenitze.softpro.Player;
import de.andrenitze.softpro.domains.employees.Employee;
import de.andrenitze.softpro.domains.employees.StatusEffect;
import de.andrenitze.softpro.domains.employees.StatusEffectType;
import de.andrenitze.softpro.domains.projects.Project;
import de.andrenitze.softpro.services.EmployeeService;
import de.andrenitze.softpro.services.MessagingService;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import static de.andrenitze.softpro.services.impl.ProjectServiceImpl.FAMILIARIZATION_WITH_NEW_DOMAIN;
import static de.andrenitze.softpro.services.impl.ProjectServiceImpl.FAMILIARIZATION_WITH_NEW_TYPE;

public class EmployeeServiceImpl implements EmployeeService {
    @Setter private Game game;
    @Setter
    private MessagingService messagingService;
    private final ProjectEmployeeMappingImpl projectEmployeeMap;
    public static final double DAYS_TO_LEARN_NEW_THINGS = 180; // 6 months to learn something new

    @Autowired
    public EmployeeServiceImpl(@Qualifier("projectEmployeeMappingImpl") ProjectEmployeeMappingImpl projectEmployeeMap) {
        this.projectEmployeeMap = projectEmployeeMap;
        this.messagingService = null;
    }

    public void simulateEmployeeLives() {
        game.getPlayerService().getPlayers().forEach((_, player) -> player.getEmployees().forEach(employee -> {
            employee.liveLife(game.getTick());
            boolean needsUpdate = employee.isSick() || employee.hasFirstDayAfterSickLeave(game.getTick()) || employee.removeExpiredStatusEffects();

            // Annual events that affect employees
            if (game.getTick() % 365 == 0) {
                employee.initializeSickDays();
            }

            // Monthly events that affect employees
            if (game.getTick() % 30 == 0) {
                // Send at least one update per month for metrics (i.e., utilization, sick days, satisfaction)
                needsUpdate = true;
            }

            // This could be refactored so that the "needsUpdate" logic can be used here as well
            applyStatusEffectsForStressfulOnboarding(player, employee);

            // Send an employee update, if anything has changed
            if (needsUpdate) {
                messagingService.sendEmployeeUpdate(player, employee);
            }
        }));
    }

    public void applyStatusEffectsForStressfulOnboarding(Player player, Employee employee) {
        if (projectEmployeeMap.isEmployeeAssignedToProject(employee)) {
            projectEmployeeMap.getProjectEmployeesMap().forEach((project, _) -> {
                if (project.getStartedAt() == 0) {
                    return;
                }
                applyStatusEffectForProjectType(employee, project);
                applyStatusEffectForProjectDomain(employee, project);
                game.getMessagingService().sendEmployeeUpdate(player, employee);
            });
        }
    }


    private void applyStatusEffectForProjectType(Employee employee, Project project) {
        StatusEffect newProjectTypeEffect = new StatusEffect(StatusEffectType.SATISFACTION, 0.7f, FAMILIARIZATION_WITH_NEW_TYPE);
        if (employee.getExperienceByType(project.getType()) < DAYS_TO_LEARN_NEW_THINGS && !employee.getStatusEffects().contains(newProjectTypeEffect)) {
            employee.addStatusEffect(newProjectTypeEffect);
        }
    }

    private void applyStatusEffectForProjectDomain(Employee employee, Project project) {
        StatusEffect newProjectDomainEffect = new StatusEffect(StatusEffectType.SATISFACTION, 0.85f, FAMILIARIZATION_WITH_NEW_DOMAIN);
        if (employee.getExperienceByDomain(project.getDomain()) < DAYS_TO_LEARN_NEW_THINGS && !employee.getStatusEffects().contains(newProjectDomainEffect)) {
            employee.addStatusEffect(newProjectDomainEffect);
        }
    }

}
