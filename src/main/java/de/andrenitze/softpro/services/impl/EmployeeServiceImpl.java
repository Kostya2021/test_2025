package de.andrenitze.softpro.services.impl;

import de.andrenitze.softpro.domains.employees.utils.EmployeeUtils;
import de.andrenitze.softpro.domains.projects.ProjectType;
import lombok.RequiredArgsConstructor;
import org.java_websocket.WebSocket;

import de.andrenitze.softpro.domains.employees.Employee;
import de.andrenitze.softpro.domains.employees.StatusEffect;
import de.andrenitze.softpro.domains.employees.StatusEffectType;
import de.andrenitze.softpro.domains.players.Player;
import de.andrenitze.softpro.domains.projects.Project;
import de.andrenitze.softpro.events.EventType;
import de.andrenitze.softpro.events.GameEvent;
import de.andrenitze.softpro.services.EmployeeService;
import de.andrenitze.softpro.services.MessagingService;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static de.andrenitze.softpro.GameServer.RANDOM;
import static de.andrenitze.softpro.services.impl.ProjectServiceImpl.FAMILIARIZATION_WITH_NEW_DOMAIN;
import static de.andrenitze.softpro.services.impl.ProjectServiceImpl.FAMILIARIZATION_WITH_NEW_TYPE;

@RequiredArgsConstructor
public class EmployeeServiceImpl implements EmployeeService {
    private final MessagingService messagingService;
    private final ProjectEmployeeMappingImpl projectsEmployeesMap;
    public static final double DAYS_TO_LEARN_NEW_THINGS = 180; // 6 months to learn something new



    public void simulateEmployeeLives(int gameTick, ConcurrentHashMap<WebSocket, Player> players) {

        players.forEach((ignored, player) -> player.getEmployees().forEach(employee -> {
            employee.liveLife(gameTick);
            boolean needsUpdate = employee.isSick() || employee.hasFirstDayAfterSickLeave(gameTick) || employee.removeExpiredStatusEffects();

            // Annual events that affect employees
            if (gameTick % 365 == 0) {
                //employee.initializeSickDays(); было так
                employee.initializeSickDays(EmployeeUtils.calculateAnnualSickDays()); //проверить на потокобезопастность!
            }

            // Monthly events that affect employees
            if (gameTick % 30 == 0) {
                // Send at least one update per month for metrics (i.e., utilization, sick days, satisfaction)
                needsUpdate = true;
            }

            if (applyStatusEffectsForStressfulOnboarding(employee)) {
                needsUpdate = true;
            }

            employee.setUpdated(needsUpdate);
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

    public boolean applyStatusEffectsForStressfulOnboarding(Employee employee) {
        boolean addedAnyEffect = false;

        if (!projectsEmployeesMap.isEmployeeAssignedToAnyProject(employee)) {
            return false;
        }

        for (Map.Entry<Project, ArrayList<Employee>> entry : projectsEmployeesMap.getProjectEmployeesMap().entrySet()) {
            Project project = entry.getKey();

            if (project.getStartedAt() == 0) continue;

            boolean typeEffectAdded = applyStatusEffectForProjectType(employee, project);
            boolean domainEffectAdded = applyStatusEffectForProjectDomain(employee, project);

            addedAnyEffect |= (typeEffectAdded || domainEffectAdded);
        }

        return addedAnyEffect;
    }

    private boolean applyStatusEffectForProjectType(Employee employee, Project project) {
        if (employee.getExperienceByType(project.getType()) >= DAYS_TO_LEARN_NEW_THINGS) {
            return false;
        }

        StatusEffect newEffect = new StatusEffect(StatusEffectType.SATISFACTION, 0.7f, FAMILIARIZATION_WITH_NEW_TYPE);
        newEffect.setTrigger(project);

        boolean alreadyPresent = employee.getStatusEffects().stream()
                .anyMatch(e -> e.getType() == newEffect.getType()
                        && e.getDescription().equals(newEffect.getDescription())
                        && Objects.equals(e.getTrigger(), project));

        if (!alreadyPresent) {
            employee.addStatusEffect(newEffect);
            return true;
        }

        return false;
    }


    private boolean applyStatusEffectForProjectDomain(Employee employee, Project project) {
        if (employee.getExperienceByDomain(project.getDomain()) >= DAYS_TO_LEARN_NEW_THINGS) {
            return false;
        }

        StatusEffect newEffect = new StatusEffect(StatusEffectType.SATISFACTION, 0.85f, FAMILIARIZATION_WITH_NEW_DOMAIN);
        newEffect.setTrigger(project);

        return employee.addStatusEffect(newEffect);
    }


    //методы с бизнесс логикой которые перенес из класса employee--->
    /**
     * Employee gains experience in a project.
     * XP in days is stored in projectExperience AND projectTypeExperience AND projectDomainExperience.
     */
    //проверить правильно ли перенес метод из класса employee!!!
    public void gainExperience(Employee employee, Project project, int newExperienceInDays) {
        // Don't gain experience in compliance projects

        if (project.getType() == ProjectType.COMPLIANCE) {
            return;
        }

        if (newExperienceInDays > 0) {
            HashMap<Project, Integer> projectExperience = employee.getProjectExperience();

            int existingExperience = projectExperience.computeIfAbsent(project, ignored -> 0);
            projectExperience.put(project, ++existingExperience);

            employee.addXp(project.getType(), project.getDomain(), newExperienceInDays);
        }
    }



}
