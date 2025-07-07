package de.andrenitze.softpro.domains.employees;

import lombok.RequiredArgsConstructor;

import de.andrenitze.softpro.GlobalIdManager;

@RequiredArgsConstructor
public class EmployeeIdGenerator {
    public int generateId() {
        return GlobalIdManager.generateEmployeeId();
    }
}
