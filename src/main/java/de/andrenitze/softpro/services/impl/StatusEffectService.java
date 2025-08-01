package de.andrenitze.softpro.services.impl;

import de.andrenitze.softpro.domains.employees.Employee;
import de.andrenitze.softpro.domains.employees.StatusEffectType;
import de.andrenitze.softpro.domains.employees.utils.EmployeeUtils;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class StatusEffectService {

    public static final String TEAM_SPIRIT = "team-spirit";
    public static final String CRUNCH_MODE = "crunch-mode";

    public static final String PROJECT_MANAGEMENT_FOUNDATION = "Project Management Foundation";
    public static final String PROJECT_MANAGEMENT_EXPERT = "Project Management Expert";

    //В IT и геймдеве crunch mode — это когда компания заставляет сотрудников работать сверхурочно, ночами, без выходных, чтобы успеть к релизу или дедлайну
    //Какие ещё сценарии могут появиться (и почему это “система”, а не Employee) --->
    //Мотивация бонусами — менеджер назначает премию, сотрудники временно счастливее
    //Сокращение — система решает: “Сотрудники теряют мотивацию, падает продуктивность
    //то есть тут внешний сценарий - это правила игры/компании - игрок сам не может решать
    //Эти сценарии не живут в Employee, потому что он их не придумывает, он просто получает их и реагирует
    //вынес бы пока в EmployeeServiceImpl если будет расти количество сценариев то тогда в StatusEffectService

    public void addComplexStatusEffect(Employee employee, String effect) {
        log.debug("Applying {} to {}", effect, employee.getName());

        // Effect "crunch-mode" will do: --->режим аврала/запары
        // +50% productivity
        // -20% satisfaction
        // -10% health (absolute, recovers only slowly)
        // Slightly increased chance of sick days
        if (effect.equals(CRUNCH_MODE)) {
            int cooldown = 20;
            employee.addStatusEffect(StatusEffectType.PRODUCTIVITY, 1.5f, CRUNCH_MODE, cooldown);
            employee.addStatusEffect(StatusEffectType.SATISFACTION, 0.8f, CRUNCH_MODE, cooldown);
            employee.addStatusEffect(StatusEffectType.HEALTH, 0.90f, CRUNCH_MODE, cooldown);

            // Increment max and annual sick days with every "crunch mode", because it's stressful
            //remainingAnnualSickDays += 1;
            employee.increaseRemainingAnnualSickDays(1);
            employee.increaseMaximumSickDays(1);
            employee.increaseSickDayProbability(0.01f);
        }
        else

            // Effect "team-spirit" will do:
            // -5% productivity (no cooldown = forever)
            // +15% satisfaction (forever)
            // +15% health (forever)
            if (effect.equals(TEAM_SPIRIT)) {
                employee.addStatusEffect(StatusEffectType.PRODUCTIVITY, 0.95f, TEAM_SPIRIT);
                employee.addStatusEffect(StatusEffectType.SATISFACTION, 1.15f, TEAM_SPIRIT);
                employee.addStatusEffect(StatusEffectType.HEALTH, 1.15f, TEAM_SPIRIT);

                // Decrease maximum sick days by 2 because of the positive effect on health
                employee.decreaseRemainingAnnualSickDays(2);
                employee.decreaseMaximumSickDays(2);
            }
    }

    //Это как если начальник вызвал тебя на разговор, выслушал твои проблемы, дал обратную связь.
    //После этого ты чувствуешь себя более ценным и довольным → работаешь охотнее.
    //тоже в сервис бы вынес EmployeeServiceImpl или StatusEffectService
    public void haveOneToOneMeeting(Employee employee) {
        // Don't add the same effect twice
        employee.getStatusEffects().removeIf(effect -> effect.getDescription().equals("Feels heard"));

        // Add a time-limited status effect that increases satisfaction by 10% for some time
        employee.addStatusEffect(StatusEffectType.SATISFACTION, 1.1f, "Feels heard", 45);

        float satisfaction = EmployeeUtils.calculateSatisfaction(employee.getSalary(), employee.getAge(), employee.getStatusEffects());
        employee.setSatisfaction(satisfaction);

    }

    //Когда на фронтенде игрок нажимает кнопку “Отправить сотрудника на обучение” - и тогда он на время тренинга теряет PRODUCTIVITY
    //тоже бы вынес в сервис - EmployeeServiceImpl или StatusEffectService или новый TrainingService
    public void train(Employee employee, String training) {
        // Add permanent status effect after the training
        switch (training) {
            case PROJECT_MANAGEMENT_FOUNDATION -> employee.addTraining(PROJECT_MANAGEMENT_FOUNDATION);
            case PROJECT_MANAGEMENT_EXPERT -> employee.addTraining(PROJECT_MANAGEMENT_EXPERT);
            default -> log.warn("Unknown training: {}", training);
        }
    }

}
