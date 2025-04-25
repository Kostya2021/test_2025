package de.andrenitze.softpro.services.impl;

import de.andrenitze.softpro.domains.employees.Employee;
import de.andrenitze.softpro.domains.employees.StatusEffect;
import de.andrenitze.softpro.domains.employees.StatusEffectType;
import de.andrenitze.softpro.domains.players.Player;
import de.andrenitze.softpro.domains.projects.Project;
import de.andrenitze.softpro.events.EventType;
import de.andrenitze.softpro.events.GameEvent;
import de.andrenitze.softpro.services.EmployeeService;
import de.andrenitze.softpro.services.MessagingService;
import lombok.RequiredArgsConstructor;
import org.java_websocket.WebSocket;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentMap;

import static de.andrenitze.softpro.services.impl.ProjectServiceImpl.FAMILIARIZATION_WITH_NEW_DOMAIN;
import static de.andrenitze.softpro.services.impl.ProjectServiceImpl.FAMILIARIZATION_WITH_NEW_TYPE;

@Service
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@RequiredArgsConstructor
@Primary
public class EmployeeServiceImpl implements EmployeeService {
    @Lazy
    private final MessagingService messagingService;
    private final ProjectEmployeeMappingImpl projectsEmployeesMap;
    public static final double DAYS_TO_LEARN_NEW_THINGS = 180; // 6 months to learn something new

    public void simulateEmployeeLives(int gameTick, ConcurrentMap<WebSocket, Player> players) {
        players.forEach((ignored, player) -> player.getEmployees().forEach(employee -> {
            employee.liveLife(gameTick);
            boolean needsUpdate = employee.isSick() || employee.hasFirstDayAfterSickLeave(gameTick) || employee.removeExpiredStatusEffects();

            // Annual events that affect employees
            if (gameTick % 365 == 0) {
                employee.initializeSickDays();
            }

            // Monthly events that affect employees
            if (gameTick % 30 == 0) {
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

    public void dismissEmployee(Player player, Employee employee) {
        employee.removeAllStatusEffects();
        player.removeEmployee(employee);
        projectsEmployeesMap.removeEmployeeFromAllProjects(employee);

        // Send employee dismissal confirmation
        GameEvent<Employee> employeeDismissedEvent = new GameEvent<>(EventType.EMPLOYEE_DISMISSED);
        employeeDismissedEvent.setPayload(employee);
        messagingService.sendToPlayer(player, employeeDismissedEvent);

        // Send new employee to all players' TalentMarkets in the game
        GameEvent<ArrayList<Employee>> talentsAddedEvent = new GameEvent<>(EventType.TALENTS_ADDED);
        talentsAddedEvent.setPayload(new ArrayList<>(List.of(employee)));
        messagingService.broadcast(talentsAddedEvent);
    }

    public void applyStatusEffectsForStressfulOnboarding(Player player, Employee employee) {
        if (projectsEmployeesMap.isEmployeeAssignedToAnyProject(employee)) {
            projectsEmployeesMap.getProjectEmployeesMap().forEach((project, ignored) -> {
                if (project.getStartedAt() == 0) {
                    return;
                }
                applyStatusEffectForProjectType(employee, project);
                applyStatusEffectForProjectDomain(employee, project);
                messagingService.sendEmployeeUpdate(player, employee);
            });
        }
    }


    private void applyStatusEffectForProjectType(Employee employee, Project project) {
        StatusEffect newProjectTypeEffect = new StatusEffect(StatusEffectType.SATISFACTION, 0.7f, FAMILIARIZATION_WITH_NEW_TYPE);
        newProjectTypeEffect.setTrigger(project);
        if (employee.getExperienceByType(project.getType()) < DAYS_TO_LEARN_NEW_THINGS && !employee.getStatusEffects().contains(newProjectTypeEffect)) {
            employee.addStatusEffect(newProjectTypeEffect);
        }
    }

    private void applyStatusEffectForProjectDomain(Employee employee, Project project) {
        StatusEffect newProjectDomainEffect = new StatusEffect(StatusEffectType.SATISFACTION, 0.85f, FAMILIARIZATION_WITH_NEW_DOMAIN);
        newProjectDomainEffect.setTrigger(project);
        if (employee.getExperienceByDomain(project.getDomain()) < DAYS_TO_LEARN_NEW_THINGS && !employee.getStatusEffects().contains(newProjectDomainEffect)) {
            employee.addStatusEffect(newProjectDomainEffect);
        }
    }

}
