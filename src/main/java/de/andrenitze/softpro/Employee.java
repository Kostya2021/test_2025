package de.andrenitze.softpro;

public class Employee {
    private final int salary;
    private final int age;
    private int experienceInDays;

    Employee() {
        salary = 3000;
        age = 30;
    }

    public int getSalary() {
        return salary;
    }

    public int getAge() {
        return age;
    }

    public int getExperienceInDays() {
        return experienceInDays;
    }

    public void setExperienceInDays(int experienceInDays) {
        this.experienceInDays = experienceInDays;
    }
}
