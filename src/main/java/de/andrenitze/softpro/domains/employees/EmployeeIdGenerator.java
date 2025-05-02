package de.andrenitze.softpro.domains.employees;

import de.andrenitze.softpro.GlobalIdManager;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class EmployeeIdGenerator {
    public int generateId() {
        return GlobalIdManager.generateGlobalId();
    }
}
