package de.andrenitze.softpro.domains.employees.utils;

import de.andrenitze.softpro.domains.employees.StatusEffect;
import de.andrenitze.softpro.domains.employees.StatusEffectType;

import java.util.List;

import static de.andrenitze.softpro.GameServer.RANDOM;
import static de.andrenitze.softpro.domains.employees.Employee.MINIMUM_AGE;

public final class EmployeeUtils {

    public static final int JOB_SATISFACTION = 50;
    private static final int MINIMUM_SICK_DAYS = 4;
    private static final int maximumSickDays = 20; //сделал пока константой

    private EmployeeUtils() { /* no instances */ }

    //просто рассчитывает удовлетворенность сотрудника от 1 до 100 вроде % - на основание разных факторов - зарплаты возраста и тд
    //потом может доработать - переменна
    public static float calculateSatisfaction(int salary, int age, List<StatusEffect> statusEffects) {
        double salaryInThousands = salary / 1000.0;
        double otherSatisfactionFactors = calculateOtherSatisfactionFactors(); //просто пока dummy value = 50
        double baseSatisfaction = calculateBaseSatisfaction(age);//значение от 5 до 20 - зависит от age

        // Calculate the salary component
        double salaryComponent = (Math.log(salaryInThousands) * 30 + Math.sqrt(salaryInThousands) * 20);
        salaryComponent = Math.min(salaryComponent, 100);

        // Weighted components
        double salaryWeight = 0.5;
        double factorsWeight = 0.5;

        // Calculate total satisfaction
        float satisfaction = (float) ((salaryWeight * salaryComponent) + (factorsWeight * otherSatisfactionFactors) + baseSatisfaction);

        // Apply all status effects of type SATISFACTION
        //Если есть статус-эффекты типа SATISFACTION - то тогда переменную satisfaction умножаем на этот эффект и satisfaction увелич по логике
        for (StatusEffect effect : statusEffects) {
            if (effect.getType() == StatusEffectType.SATISFACTION) {
                satisfaction *= effect.getMultiplier();
            }
        }

        return Math.clamp(satisfaction, 1, 100); // Clamp to [1, 100]
        //и мы то что вернули уже засеттим в builder - через метод самого Employee
    }

    // Intrinsic satisfaction of an employee
    private static double calculateBaseSatisfaction(int age) {
        // Value between 5 and 20, depending on age
        return Math.clamp(20L - (age - MINIMUM_AGE), 5, 20);
    }

    // Job satisfaction factors not related to salary
    private static int calculateOtherSatisfactionFactors() {
        // Dummy value, refine later (work environment, career opportunities, mentoring etc.)
        // Satisfaction 0-100
        return JOB_SATISFACTION;
    }


    public static int calculateAnnualSickDays() {
        // Randomize the number of sick days an employee can have in a year
        return MINIMUM_SICK_DAYS + RANDOM.nextInt(maximumSickDays - MINIMUM_SICK_DAYS);
    }

}
