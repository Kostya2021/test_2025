package de.andrenitze.softpro;

public class EmployeeIdGenerator {
    public int generateId() {
        return GlobalIdManager.generateGlobalId();
    }
}
