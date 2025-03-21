package de.andrenitze.softpro.domains.employees;

import de.andrenitze.softpro.GlobalIdManager;

public class EmployeeIdGenerator {
    public int generateId() {
        return GlobalIdManager.generateGlobalId();
    }
}
