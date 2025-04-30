package de.andrenitze.softpro.domains;

import de.andrenitze.softpro.domains.accounting.AccountingEntry;
import de.andrenitze.softpro.domains.decisions.Decision;
import de.andrenitze.softpro.domains.players.Player;
import de.andrenitze.softpro.domains.projects.Project;
import de.andrenitze.softpro.domains.skills.Skill;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class GameState {
    private int level;
    private Player player;
    private HashMap<String, Skill> skills;
    private List<AccountingEntry> accountingEntries;
    private List<Project> projects;
    private Map<Integer, List<Decision>> decisions;

    public void setProjects(List<Project> projects) {
        // Remove involved parties to prevent circular references
        projects.forEach(Project::clearInvolvedParties);
        this.projects = projects;
    }

    public void setPlayer(Player player) {
        // Remove missions as they are initialized with the new level
        player.clearMissions();
        this.player = player;
    }
}