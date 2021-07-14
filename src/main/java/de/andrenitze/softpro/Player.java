package de.andrenitze.softpro;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import de.andrenitze.softpro.entities.Objective;
import de.andrenitze.softpro.entities.Objectives;
import de.andrenitze.softpro.types.EventType;
import de.andrenitze.softpro.events.GameEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Player {
    @JsonIgnore
    private final UUID id;

    @JsonProperty
    private String name;

    @JsonProperty
    private String company;

    @JsonProperty
    private double funds = 50000;

    @JsonProperty
    private final ArrayList<Employee> employees = new ArrayList<>();

    @JsonIgnore
    private final ArrayList<Objective> objectives;

    Player() {
        this("Unknown player", "Unknown company");
    }

    Player(String name, String company) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.company = company;

        employees.add(new Employee());
        employees.add(new Employee());

        Objectives objectives = new Objectives();
        objectives.loadObjectivesFromYamlFile();
        this.objectives = objectives.getObjectives();
    }

    String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public double addFunds(double additionalFunds) {
        this.funds += additionalFunds;
        return funds;
    }

    private void subtractFunds(double fundsToSubtract) {
        this.funds -= fundsToSubtract;
    }

    public double getFunds() {
        return funds;
    }

    public String getCompany() {
        return company;
    }

    ArrayList<Employee> getEmployees() {
        return employees;
    }

    void calculateAndSubtractSalaries() {
        employees.forEach(employee -> this.subtractFunds(employee.getSalary()));
    }

    Employee getEmployeeById(int id) {
        for (Employee employee : employees) {
            if (employee.getId() == id) {
                return employee;
            }
        }
        return null;
    }

    public UUID getId() {
        return id;
    }

    public ArrayList<Objective> getObjectives() {
        return objectives;
    }

    public GameEvent<Object> createGameEventOfChangedObjectives() {
        GameEvent<Object> event = new GameEvent<>(EventType.UPDATE_STATE);

        return event.getPayload() != null? null : event;
    }

    public ArrayList<Objective> getNewObjectivesForThisTick(int tick) {
        ArrayList<Objective> allObjectives = this.getObjectives();
        ArrayList<Objective> newObjectivesForThisTick = new ArrayList<>();
        allObjectives.forEach(objective -> {
            if (objective.getEarliestOccurrence() == tick) {
                newObjectivesForThisTick.add(objective);
            }
        });
        return newObjectivesForThisTick;
    }
    public ArrayList<Objective> getActiveObjectivesUntilThisTick(int tick) {
        ArrayList<Objective> allObjectives = getObjectives();
        ArrayList<Objective> allActiveObjectives = new ArrayList<>();
        allObjectives.forEach(objective -> {
            if (objective.getEarliestOccurrence() == 0 ||
                    objective.getEarliestOccurrence() <= tick) {
                allActiveObjectives.add(objective);
            }
        });
        return allActiveObjectives;
    }
}
