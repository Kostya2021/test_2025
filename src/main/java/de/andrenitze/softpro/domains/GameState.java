package de.andrenitze.softpro.domains;

import de.andrenitze.softpro.domains.decisions.Decision;
import de.andrenitze.softpro.domains.employees.Employee;
import de.andrenitze.softpro.domains.skills.Skill;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.List;

@Getter
@Setter
public class GameState {
    private int xp;
    private int level;
    private List<Employee> employees;
    private List<Decision> decisions;
    private HashMap<String, Skill> skills;
}