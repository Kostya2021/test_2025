package de.andrenitze.softpro;

public class GlobalTalentMarket {
    private static final TalentMarket instance = new TalentMarket(new EmployeeIdGenerator());

    public static TalentMarket getInstance() {
        return instance;
    }
}
