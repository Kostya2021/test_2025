package de.andrenitze.softpro.services;

import de.andrenitze.softpro.domains.players.Player;
import de.andrenitze.softpro.domains.skills.Skill;

import java.util.HashMap;

/**
 * Service für die Verwaltung von Spieler-Skills.
 */
public interface SkillService {
    boolean playerHasSkill(Player player, String skillName);
    void saveSkills(Player player);
    void loadSkills(Player player);
    void addPermanentStatusEffectsToAllEmployees();
    void unlockSkill(Player player, String skillName, int unlockSkillPoints);
    void setSkills(Player player, HashMap<String, Skill> skillsMap);
    void initializePlayer(Player player);
}