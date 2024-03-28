package de.andrenitze.softpro;

public class EmployeeIdGenerator {
    private int lastId = 1;

    public synchronized int generateId() {
        return lastId++;
    }
}
