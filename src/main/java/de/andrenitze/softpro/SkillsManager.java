package de.andrenitze.softpro;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import de.andrenitze.softpro.domains.skills.Skill;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

import static de.andrenitze.softpro.events.GameEventHandler.TEAM_SPIRIT;
import static de.andrenitze.softpro.Main.logger;

public class SkillsManager {
    private final ConcurrentHashMap<Player, HashMap<String, Skill>> playersSkills;
    private static final Gson gson = new Gson();
    private static final String SKILLS_DIRECTORY = "skills/";

    protected static final int[] XP_LEVEL_THRESHOLDS = {125, 250, 500, 1000, 2500, 8000, 15000, 20000, 30000};

    public SkillsManager() {
        playersSkills = new ConcurrentHashMap<>();
        logger.debug("SkillsManager initialized.");
        loadAllSkills();
    }

    public void unlockSkill(Player player, String skillId, int unlockSkillPoints) {
        Skill skill = new Skill();
        skill.setId(skillId);
        skill.setUnlocked(true);

        player.setSkillPoints(player.getSkillPoints() - unlockSkillPoints);

        HashMap<String, Skill> skills = playersSkills.get(player);
        skills.putIfAbsent(skillId, skill);
        playersSkills.put(player, skills);
        logger.debug("Player {} unlocked skill {}", player.getName(), skillId);
        saveSkills(player);
        addPermanentStatusEffectsToAllEmployees();
    }

    public boolean playerHasSkill(Player player, String skillId) {
        HashMap<String, Skill> skills = playersSkills.get(player);
        if (skills == null) {
            logger.debug("Player {} has no skills registered.", player.getName());
            return false;
        }

        Skill skill = skills.get(skillId);
        return skill != null && skill.isUnlocked();
    }

    void addPlayer(Player player) {
        if (playersSkills.containsKey(player)) {
            logger.debug("Player {} already exists in the skillsManager. No override will occur.", player.getName());
        } else {
            logger.debug("Adding new player {} to the skillsManager.", player.getName());
            loadSkills(player);
        }
        playersSkills.putIfAbsent(player, new HashMap<>());
    }

    public HashMap<String, Skill> getSkillsByPlayer(Player player) {
        return playersSkills.get(player);
    }

    private void saveSkills(Player player) {
        try {
            File directory = new File(SKILLS_DIRECTORY);
            if (!directory.exists() && !directory.mkdirs()) {
                throw new IllegalStateException("Failed to create directory: " + directory.getAbsolutePath());
            }
            FileWriter writer = new FileWriter(SKILLS_DIRECTORY + player.getId() + ".json");
            gson.toJson(playersSkills.get(player), writer);
            writer.close();
            logger.debug("Skills for player {} saved to {}", player.getName(), SKILLS_DIRECTORY + player.getId() + ".json");
        } catch (IOException e) {
            logger.error("Failed to save skills for player {}: {}", player.getId(), e.getMessage());
        }
    }

    private void loadSkills(Player player) {
        try {
            File file = new File(SKILLS_DIRECTORY + player.getId() + ".json");
            if (file.exists()) {
                FileReader reader = new FileReader(file);
                Type type = new TypeToken<HashMap<String, Skill>>() {}.getType();
                HashMap<String, Skill> skills = gson.fromJson(reader, type);
                playersSkills.put(player, skills);
                reader.close();
                logger.debug("Skills for player {} loaded from {}", player.getName(), SKILLS_DIRECTORY + player.getId() + ".json");
            } else {
                saveSkills(player); // Create a new file if it does not exist
                logger.debug("No existing skills file for player {}. Created a new one.", player.getName());
            }
        } catch (IOException e) {
            logger.error("Failed to load skills for player {}: {}", player.getId(), e.getMessage());
        }
    }

    private void loadAllSkills() {
        File directory = new File(SKILLS_DIRECTORY);
        if (directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles((dir, name) -> name.endsWith(".json"));
            if (files != null) {
                long currentTime = System.currentTimeMillis();
                // Maximum time after one game round is completed
                long oneHourInMillis = 60 * 60 * 1000;
                // After that, the state is persisted somewhere else

                for (File file : files) {
                    if (currentTime - file.lastModified() > oneHourInMillis) {
                        if (file.delete()) {
                            logger.debug("Deleted old skills file: {}", file.getPath());
                        } else {
                            logger.error("Failed to delete old skills file: {}", file.getPath());
                        }
                        continue;
                    }

                    try {
                        FileReader reader = new FileReader(file);
                        Type type = new TypeToken<HashMap<String, Skill>>() {}.getType();
                        HashMap<String, Skill> skills = gson.fromJson(reader, type);
                        String playerId = file.getName().replace(".json", "");
                        Player player = playersSkills.keySet().stream()
                                .filter(p -> p.getId().toString().equals(playerId))
                                .findFirst()
                                .orElse(null);
                        if (player != null) {
                            playersSkills.put(player, skills);
                            logger.debug("{} skills for player {} loaded from {}", skills.size(), player.getName(), file.getPath());
                        }
                        reader.close();
                    } catch (IOException e) {
                        logger.error("Failed to load skills from file {}: {}", file.getPath(), e.getMessage());
                    }
                }
            }
        }
    }

    public void addPermanentStatusEffectsToAllEmployees() {
        playersSkills.keySet().forEach(player -> {
            logger.debug("Checking for permanent status effects for player {}", player.getId());

            // The only permanent status effect is TEAM_SPIRIT
            if (playerHasSkill(player, TEAM_SPIRIT)) {
                logger.debug("Player {} has the skill {}", player.getId(), TEAM_SPIRIT);
                player.getEmployees().forEach(employee -> {
                    logger.debug("Adding permanent status effect {} to employee {}", TEAM_SPIRIT, employee.getId());
                    employee.addComplexStatusEffect(TEAM_SPIRIT);
                });
            }
        });
    }
}