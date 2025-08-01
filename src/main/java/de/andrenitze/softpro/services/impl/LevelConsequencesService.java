package de.andrenitze.softpro.services.impl;

import de.andrenitze.softpro.domains.employees.EmployeeBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;

import de.andrenitze.softpro.domains.employees.Employee;
import de.andrenitze.softpro.domains.employees.StatusEffectType;
import de.andrenitze.softpro.domains.employees.TalentMarket;
import de.andrenitze.softpro.services.impl.player.GamePlayerServiceImpl;

@Primary
@RequiredArgsConstructor
public class LevelConsequencesService {
    public static final String RESTORE_LOST_DATA = "Restore lost data";
    public static final int BACKUP_BLUES_LEVEL = 2;
    private final GamePlayerServiceImpl playerService;
    private final TalentMarket talentMarket;

    public void triggerLevel1Consequences() {
        playerService.getPlayers().forEach((ignored, player) -> {
            int option = player.getDecisionsByLevel(1).getFirst().getOptionId();
            if (option == 1) {
                player.getEmployees().forEach(employee -> employee.addStatusEffect(
                        StatusEffectType.PRODUCTIVITY, 1.75f, "Efficient work organization"));
            } else if (option == 2) {
                Employee freeEmployee = new EmployeeBuilder(talentMarket.generateNewEmployeeId())
                        .salary(0, 0)
                        .satisfaction(0.7f)
                        .build();
                player.addEmployee(freeEmployee);
            } else if (option == 3) {
                player.getEmployees().forEach(employee -> {
                    employee.addStatusEffect(StatusEffectType.PRODUCTIVITY, 0.8f, "Spontaneous work organization");
                    employee.addStatusEffect(StatusEffectType.SATISFACTION, 0.9f, "Spontaneous work organization");
                });
            }
        });
    }

    public void triggerLevel2Consequences() {
        playerService.getPlayers().forEach((ignored, player) -> {
            int option = player.getDecisionsByLevel(BACKUP_BLUES_LEVEL).getFirst().getOptionId();
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
        playerService.getPlayers().forEach((ignored, player) -> {
            int backupOption = player.getDecisionsByLevel(BACKUP_BLUES_LEVEL).getFirst().getOptionId();
            if (backupOption == 2) {
                player.getEmployees().forEach(employee -> employee.addStatusEffect(
                        StatusEffectType.PRODUCTIVITY, 0.6f, RESTORE_LOST_DATA));
            } else if (backupOption == 1) {
                player.getEmployees().forEach(employee -> employee.addStatusEffect(
                        StatusEffectType.PRODUCTIVITY, 0.95f, RESTORE_LOST_DATA));
            }
        });
    }

    public void triggerLevel4Consequences() {
        playerService.getPlayers().forEach((ignored, player) -> {
            int backupOption = player.getDecisionsByLevel(BACKUP_BLUES_LEVEL).getFirst().getOptionId();
            if (backupOption == 2) {
                player.getEmployees().forEach(employee -> employee.addStatusEffect(
                        StatusEffectType.PRODUCTIVITY, 0.2f, RESTORE_LOST_DATA));
            } else if (backupOption == 1) {
                player.getEmployees().forEach(employee -> employee.addStatusEffect(
                        StatusEffectType.PRODUCTIVITY, 0.95f, RESTORE_LOST_DATA));
            }
        });
    }
}