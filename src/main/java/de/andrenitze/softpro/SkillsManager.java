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

    public SkillsManager() {
        playersSkills = new ConcurrentHashMap<>();
    }

    void unlockSkill(Player player, String skillId) {
        if (Objects.equals(skillId, "pmo")) {
            Skill skill = new Skill();
            skill.setId(skillId);
            skill.setUnlocked(true);

            HashMap<String, Skill> skills = playersSkills.get(player);
            skills.putIfAbsent(skillId, skill);
            playersSkills.put(player, skills);
        }
    }

    boolean playerHasSkill(Player player, Skill skill) {
        HashMap<String, Skill> skills = playersSkills.get(player);
        return skills.get(skill.getId()).isUnlocked();
    }

    void addPlayer(Player player) {
        playersSkills.putIfAbsent(player, new HashMap<>());
    }
}
