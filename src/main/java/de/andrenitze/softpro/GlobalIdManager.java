package de.andrenitze.softpro;

public class GlobalIdManager {
    private static int lastId = 1;

    private GlobalIdManager() {}

    public static synchronized int generateGlobalId() {
        return lastId++;
    }
}
