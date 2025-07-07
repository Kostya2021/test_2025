package de.andrenitze.softpro;

public class GlobalIdManager {
    private static int lastEmployeeId = 1;
    private static int lastProjectId = 1;

    private GlobalIdManager() {}

    public static synchronized int generateEmployeeId() {return lastEmployeeId++;}
    public static synchronized int generateProjectId() {return lastProjectId++;}
}
