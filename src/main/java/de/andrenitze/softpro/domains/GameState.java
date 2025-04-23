package de.andrenitze.softpro.domains;

import de.andrenitze.softpro.domains.accounting.AccountingEntry;
import de.andrenitze.softpro.domains.players.Player;
import de.andrenitze.softpro.domains.projects.Project;
import de.andrenitze.softpro.domains.skills.Skill;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.List;

@Getter
@Setter
public class GameState {
    private int level;
    private Player player;
    private HashMap<String, Skill> skills;
    private List<AccountingEntry> accountingEntries;
    private List<Project> projects;

    public void setProjects(List<Project> projects) {
        // Remove involved parties to prevent circular references
        projects.forEach(Project::clearInvolvedParties);
        this.projects = projects;
    }

    public void setPlayer(Player player) {
        // Remove missions as they are initialized with the new level
        if (this.player != null) {
            this.player.clearMissions();
        }

        this.player = player;
    }
}