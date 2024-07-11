package de.andrenitze.softpro;

public class GlobalIdManager {
    private static int lastId = 1;

    public static synchronized int generateGlobalId() {
        return lastId++;
    }
}
