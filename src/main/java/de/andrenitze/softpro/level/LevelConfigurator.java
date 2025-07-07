package de.andrenitze.softpro.level;

import de.andrenitze.softpro.Game;
import de.andrenitze.softpro.domains.employees.Employee;
import de.andrenitze.softpro.domains.employees.StatusEffectType;
import de.andrenitze.softpro.domains.players.Player;
import de.andrenitze.softpro.domains.projects.Project;
import de.andrenitze.softpro.domains.projects.ProjectBuilder;
import de.andrenitze.softpro.domains.projects.ProjectType;
import de.andrenitze.softpro.domains.projects.RiskLevel;
import de.andrenitze.softpro.services.ProjectService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static de.andrenitze.softpro.GameServer.RANDOM;

@Slf4j
@Component
public class LevelConfigurator {

    public void configureLevel(Game game, Player player, int level) {
        switch (level) {
            case 1 -> configureLevel1(game, player);
            case 2 -> configureLevel2(game);
            default -> log.info("No special configuration for level {}", level);
        }
    }

    private void configureLevel1(Game game, Player player) {
        player.setXp(0);
        player.setEmployees(new ArrayList<>());

        Employee employee = new Employee(game.getTalentMarket().generateNewEmployeeId());
        employee.setFirstName(player.getFirstName());
        employee.setLastName(player.getLastName());
        employee.setSalary(458, 0);
        employee.setAge(22);
        employee.addStatusEffect(StatusEffectType.PRODUCTIVITY, 1.2f, "Highly motivated");

        List<ProjectType> nonComplianceTypes = Arrays.stream(ProjectType.values())
                .filter(type -> type != ProjectType.COMPLIANCE)
                .toList();

        ProjectType randomType = nonComplianceTypes.get(RANDOM.nextInt(nonComplianceTypes.size()));
        String domain = randomType.getRandomDomain();
        employee.addXp(randomType, domain, 400);
        player.addEmployee(employee);

        ProjectService projectService = game.getProjectService();
        projectService.addProject(
                new ProjectBuilder().
                        type(randomType).
                        domain(domain).
                        risk(RiskLevel.LOW).
                        hasTenderProcess(false).
                        build());

        for (int i = 0; i < 2; i++) {
            ProjectType another = nonComplianceTypes.get(RANDOM.nextInt(nonComplianceTypes.size()));
            projectService.addProject(
                    new ProjectBuilder().
                            type(another).
                            domain(another.getRandomDomain()).
                            risk(RiskLevel.LOW).
                            hasTenderProcess(false).
                            build()
            );
        }

        log.debug("Level 1 initialized for player {}", player.getId());
    }

    private void configureLevel2(Game game) {
        game.getPlayerService().generateFirstEmployeesForPlayers();
        log.debug("Level 2 initialized: first employees generated.");
    }
}
