package de.andrenitze.softpro;

import de.andrenitze.softpro.entities.Skill;

import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The SkillsManager holds the players' skills in a game and provides convenience methods for the core game loop.
 */
public class SkillsManager {
    private final ConcurrentHashMap<Player, HashMap<String, Skill>> playersSkills;

    // Required XP to progress to the next level and earn a skill point
    protected static final int[] LEVEL_THRESHOLDS = {250, 500, 1000, 2000, 4000, 8000, 15000, 20000, 30000};

    public SkillsManager() {
        playersSkills = new ConcurrentHashMap<>();
    }

    void unlockSkill(Player player, String skillId, int unlockSkillPoints) {
        if (Objects.equals(skillId, "pmo")) {
            Skill skill = new Skill();
            skill.setId(skillId);
            skill.setUnlocked(true);

            // Decrease the player's skill points
            player.setSkillPoints(player.getSkillPoints() - unlockSkillPoints);

            HashMap<String, Skill> skills = playersSkills.get(player);
            skills.putIfAbsent(skillId, skill);
            playersSkills.put(player, skills);
        }
    }

    boolean playerHasSkill(Player player, String skillId) {
        HashMap<String, Skill> skills = playersSkills.get(player);
        return skills.get(skillId) != null && skills.get((skillId)).isUnlocked();
    }

    void addPlayer(Player player) {
        playersSkills.putIfAbsent(player, new HashMap<>());
    }
}
