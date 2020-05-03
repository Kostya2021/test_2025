package de.andrenitze.softpro;

public class Employee {
    private int salary;
    private int age;
    private int timeSpentInProjects;

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
}
