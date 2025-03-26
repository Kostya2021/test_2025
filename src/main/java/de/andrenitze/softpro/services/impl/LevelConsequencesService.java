package de.andrenitze.softpro.services.impl;

import de.andrenitze.softpro.TalentMarket;
import de.andrenitze.softpro.domains.employees.Employee;
import de.andrenitze.softpro.domains.employees.StatusEffectType;
import de.andrenitze.softpro.Game;
import de.andrenitze.softpro.services.impl.player.GamePlayerServiceImpl;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
public class LevelConsequencesService {
    private final GamePlayerServiceImpl playerService;
    private final TalentMarket talentMarket;

    public LevelConsequencesService(GamePlayerServiceImpl playerService, TalentMarket talentMarket) {
        this.playerService = playerService;
        this.talentMarket = talentMarket;
    }

    public void triggerLevel1Consequences() {
        playerService.getPlayers().forEach((_, player) -> {
            int option = player.getDecisionsByLevel(1).getFirst().getOptionId();
            if (option == 1) {
                player.getEmployees().forEach(employee -> employee.addStatusEffect(
                        StatusEffectType.PRODUCTIVITY, 1.75f, "Efficient work organization"));
            } else if (option == 2) {
                Employee freeEmployee = new Employee(talentMarket.generateNewEmployeeId());
                freeEmployee.setSalary(0, 0);
                freeEmployee.setSatisfaction(0.7f);
                player.addEmployee(freeEmployee, 0);
            } else if (option == 3) {
                player.getEmployees().forEach(employee -> {
                    employee.addStatusEffect(StatusEffectType.PRODUCTIVITY, 0.8f, "Spontaneous work organization");
                    employee.addStatusEffect(StatusEffectType.SATISFACTION, 0.9f, "Spontaneous work organization");
                });
            }
        });
    }

    public void triggerLevel2Consequences() {
        playerService.getPlayers().forEach((_, player) -> {
            int option = player.getDecisionsByLevel(Game.BACKUP_BLUES_LEVEL).getFirst().getOptionId();
            if (option == 1) {
                player.setFunds(player.getFunds() - 5000);
                Employee firstEmployee = player.getEmployees().getFirst();
                firstEmployee.addStatusEffect(StatusEffectType.PRODUCTIVITY, 0.8f, "Implementing backup solution");
            } else if (option == 3) {
                player.setFunds(player.getFunds() - 15000);
            }
        });
    }

    public void triggerLevel3Consequences() {
        playerService.getPlayers().forEach((_, player) -> {
            int backupOption = player.getDecisionsByLevel(Game.BACKUP_BLUES_LEVEL).getFirst().getOptionId();
            if (backupOption == 2) {
                player.getEmployees().forEach(employee -> employee.addStatusEffect(
                        StatusEffectType.PRODUCTIVITY, 0.6f, Game.RESTORE_LOST_DATA));
            } else if (backupOption == 1) {
                player.getEmployees().forEach(employee -> employee.addStatusEffect(
                        StatusEffectType.PRODUCTIVITY, 0.95f, Game.RESTORE_LOST_DATA));
            }
        });
    }

    public void triggerLevel4Consequences() {
        playerService.getPlayers().forEach((_, player) -> {
            int backupOption = player.getDecisionsByLevel(Game.BACKUP_BLUES_LEVEL).getFirst().getOptionId();
            if (backupOption == 2) {
                player.getEmployees().forEach(employee -> employee.addStatusEffect(
                        StatusEffectType.PRODUCTIVITY, 0.2f, Game.RESTORE_LOST_DATA));
            } else if (backupOption == 1) {
                player.getEmployees().forEach(employee -> employee.addStatusEffect(
                        StatusEffectType.PRODUCTIVITY, 0.95f, Game.RESTORE_LOST_DATA));
            }
        });
    }
}