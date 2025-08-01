package de.andrenitze.softpro.domains.employees;


import de.andrenitze.softpro.domains.employees.generators.EmployeeNameGenerator;
import de.andrenitze.softpro.domains.employees.utils.EmployeeUtils;
import de.andrenitze.softpro.domains.projects.ProjectType;
import de.andrenitze.softpro.services.impl.EmployeeServiceImpl;

import java.util.EnumMap;

import static de.andrenitze.softpro.GameServer.RANDOM;
import static de.andrenitze.softpro.domains.employees.Employee.MINIMUM_AGE;

//теперь проверить как создавались объекты класса employee - и нужно ли сюда добавлять методы для ручной установки полей!!!
//поменять везде создание объектов класса employee
//потом доработать - как показывал через boolean - чтобы поля который сами вручную настраиваем не генерировались уже рандомно
public class EmployeeBuilder {

    private static final EmployeeNameGenerator EMPLOYEE_NAME_GENERATOR = EmployeeNameGenerator.getInstance();

    public static final int NUMBER_OF_PROJECTS_TO_HAVE_EXPERIENCE_IN = 3;
    private static final int MAX_DAYS_OF_XP = 365;


    private final Employee employee = new Employee();


    public EmployeeBuilder(Integer id) {
        employee.setId(id); //понять почему id-это integer - а например age это просто int - хотя вроде тоже один раз может задаваться - хотя наверное каждый год в будущем менять будем

        String[] generatedName = EMPLOYEE_NAME_GENERATOR.generateName();
        employee.setFirstName(generatedName[0]);
        employee.setLastName(generatedName[1]);
        employee.setGender(generatedName[2]);

        // Randomize salary
        employee.setSalary(RANDOM.nextInt(0, 1500) + 1500, 0);

        // Randomize age between 20 and 60
        employee.setAge(RANDOM.nextInt(40) + MINIMUM_AGE);

        float satisfaction = EmployeeUtils.calculateSatisfaction(employee.getSalary(), employee.getAge(), employee.getStatusEffects());
        employee.setSatisfaction(satisfaction);

        int annualSickDays = EmployeeUtils.calculateAnnualSickDays();
        employee.initializeSickDays(annualSickDays);

        initProjectTypeExperience();
        assignInitialExperience();

    }


    /** Создаёт запись для каждого типа проекта с нулевым опытом. */
    private void initProjectTypeExperience() {
        EnumMap<ProjectType, Integer> projectTypeExperience = employee.getProjectTypeExperience();
        for (ProjectType type : ProjectType.values()) {
            projectTypeExperience.put(type, 0);
        }
    }

    /** Добавляет опыт (в днях) в несколько случайных типов проектов и относящимся к нему домену */
    // Add some days of experience in a few of the project types --->
    // и соответственно относящихся к ним domains
    private void assignInitialExperience() {
        for (int i = 0; i < NUMBER_OF_PROJECTS_TO_HAVE_EXPERIENCE_IN; i++) {
            int projectTypeIndex = RANDOM.nextInt(ProjectType.values().length);
            int days = RANDOM.nextInt(MAX_DAYS_OF_XP);

            // Now, add some days of experience in one project domain of this type
            ProjectType type = ProjectType.values()[projectTypeIndex];
            employee.addXp(type, type.getRandomDomain(), days); //type.getRandomDomain() - тут выбрали случайным образом один из domains относящийся к projecttype
        }

        }



    public EmployeeBuilder firstName(String firstName) {
        employee.setFirstName(firstName);
        return this;
    }

    public EmployeeBuilder lastName(String lastName) {
        employee.setLastName(lastName);
        return this;
    }

    public EmployeeBuilder salary(int salary, int tick) {
        employee.setSalary(salary, tick);
        return this;
    }

    public EmployeeBuilder age(int age) {
        employee.setAge(age);
        return this;
    }

    public EmployeeBuilder statusEffect(StatusEffectType type, float multiplier, String description) {
        employee.addStatusEffect(type, multiplier, description);
        return this;
    }

    public EmployeeBuilder satisfaction(float satisfaction) {
        employee.setSatisfaction(satisfaction);
        return this;
    }

    public EmployeeBuilder xp(ProjectType type, String domain, int days) {
        employee.addXp(type, domain, days);
        return this;
    }

    public Employee build() {
        return employee;
    }


    //добавить сеттеры для этих методов ---> для класса LevelConfigurator
    //employee.setFirstName(player.getFirstName());
    //        employee.setLastName(player.getLastName());
    //        employee.setSalary(458, 0);
    //        employee.setAge(22);
    //        employee.addStatusEffect(StatusEffectType.PRODUCTIVITY, 1.2f, "Highly motivated");


}
