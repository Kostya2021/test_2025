package de.andrenitze.softpro.services.impl;

import com.google.gson.reflect.TypeToken;
import de.andrenitze.softpro.GameServer;
import de.andrenitze.softpro.domains.players.Player;
import de.andrenitze.softpro.domains.skills.Skill;
import de.andrenitze.softpro.services.SkillService;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static de.andrenitze.softpro.events.GameEventHandler.TEAM_SPIRIT;

@Setter
@Getter
public class SkillServiceImpl implements SkillService {
    private static final Logger log = LoggerFactory.getLogger(SkillServiceImpl.class);
    public static final String JSON = ".json";
    private ConcurrentHashMap<Player, HashMap<String, Skill>> playersSkills;
    private static final String SKILLS_DIRECTORY = "skills/";

    public SkillServiceImpl() {
        playersSkills = new ConcurrentHashMap<>();

        try {
            loadAllSkills();
        } catch (IOException e) {
            log.error("Failed to load skills: {}", e.getMessage());
        }
    }

    public void unlockSkill(Player player, String skillName, int unlockSkillPoints) {
        Skill skill = new Skill();
        skill.setId(skillName);
        skill.setUnlocked(true);

        player.setSkillPoints(player.getSkillPoints() - unlockSkillPoints);

        HashMap<String, Skill> skills = playersSkills.get(player);
        skills.putIfAbsent(skillName, skill);
        playersSkills.put(player, skills);
        log.debug("Player {} unlocked skill {}", player.getId(), skillName);
        saveSkills(player);
        addPermanentStatusEffectsToAllEmployees();
    }

    @Override
    public void setSkills(Player player, HashMap<String, Skill> skills) {
        if (playersSkills.containsKey(player)) {
            playersSkills.put(player, skills);
            log.debug("Skills for player {} set to {}", player.getId(), skills);
        } else {
            log.warn("Player {} not found in skills manager. Cannot set skills.", player.getId());
        }
    }

    public boolean playerHasSkill(Player player, String skillName) {
        HashMap<String, Skill> skills = playersSkills.get(player);
        if (skills == null) {
            return false;
        }

        Skill skill = skills.get(skillName);
        return skill != null && skill.isUnlocked();
    }

    public void addPlayer(Player player) {
        if (playersSkills.containsKey(player)) {
            log.debug("Player {} already exists in the skillsManager. No override will occur.", player.getId());
        } else {
            loadSkills(player);
        }
        playersSkills.putIfAbsent(player, new HashMap<>());
    }

    public Map<String, Skill> getSkillsByPlayer(Player player) {
        return playersSkills.get(player);
    }

    public void saveSkills(Player player) {
        try {
            File directory = new File(SKILLS_DIRECTORY);
            if (!directory.exists() && !directory.mkdirs()) {
                throw new IllegalStateException("Failed to create directory: " + directory.getAbsolutePath());
            }
            FileWriter writer = new FileWriter(SKILLS_DIRECTORY + player.getId() + JSON);
            GameServer.getGson().toJson(playersSkills.get(player), writer);
            writer.close();
            log.debug("Skills for player saved to {}", SKILLS_DIRECTORY + player.getId() + JSON);
        } catch (IOException e) {
            log.error("Failed to save skills for player {}: {}", player.getId(), e.getMessage());
        }
    }

    public void loadSkills(Player player) {
        try {
            File file = new File(SKILLS_DIRECTORY + player.getId() + JSON);
            if (file.exists()) {
                FileReader reader = new FileReader(file);
                Type type = new TypeToken<HashMap<String, Skill>>() {}.getType();
                HashMap<String, Skill> skills = GameServer.getGson().fromJson(reader, type);
                if (skills == null) {
                    skills = new HashMap<>();
                }
                playersSkills.put(player, skills);
                reader.close();
                log.debug("Skills for player {} loaded from {}", player.getId(), SKILLS_DIRECTORY + player.getId() + JSON);
            } else {
                saveSkills(player); // Create a new file if it does not exist
            }
        } catch (IOException e) {
            log.error("Failed to load skills for player {}: {}", player.getId(), e.getMessage());
        }
    }

    private void loadAllSkills() throws IOException {
        File directory = new File(SKILLS_DIRECTORY);
        if (!directory.exists() || !directory.isDirectory()) {
            return;
        }

        File[] files = directory.listFiles((ignored, name) -> name.endsWith(JSON));
        if (files == null) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        long oneHourInMillis = (long) 60 * 60 * 1000;

        for (File file : files) {
            if (isFileOld(file, currentTime, oneHourInMillis)) {
                deleteOldFile(file);
                continue;
            }
            loadSkillsFromFile(file);
        }
    }

    private boolean isFileOld(File file, long currentTime, long oneHourInMillis) {
        return currentTime - file.lastModified() > oneHourInMillis;
    }

    private void deleteOldFile(File file) throws IOException {
        try {
            if (Files.deleteIfExists(file.toPath())) {
                log.debug("Deleted old skills file: {}", file.getPath());
            } else {
                log.error("Failed to delete old skills file: {}", file.getPath());
            }
        } catch (IOException e) {
            throw new IOException();
        }
    }

    private void loadSkillsFromFile(File file) {
        try (FileReader reader = new FileReader(file)) {
            Type type = new TypeToken<HashMap<String, Skill>>() {}.getType();
            HashMap<String, Skill> skills = GameServer.getGson().fromJson(reader, type);
            String playerId = file.getName().replace(JSON, "");
            Player player = findPlayerById(playerId);
            if (player != null) {
                playersSkills.put(player, skills);
                log.debug("{} skills for player {} loaded from {}", skills.size(), player.getId(), file.getPath());
            }
        } catch (IOException e) {
            log.error("Failed to load skills from file {}: {}", file.getPath(), e.getMessage());
        }
    }

    private Player findPlayerById(String playerId) {
        return playersSkills.keySet().stream()
                .filter(p -> p.getId().toString().equals(playerId))
                .findFirst()
                .orElse(null);
    }

    public void addPermanentStatusEffectsToAllEmployees() {
        playersSkills.keySet().forEach(player -> {
            log.debug("Checking for permanent status effects for player {}", player.getId());

            // The only permanent status effect is TEAM_SPIRIT
            if (playerHasSkill(player, TEAM_SPIRIT)) {
                log.debug("Player {} has the skill {}", player.getId(), TEAM_SPIRIT);
                player.getEmployees().forEach(employee -> {
                    log.debug("Adding permanent status effect {} to employee {}", TEAM_SPIRIT, employee.getId());
                    employee.addComplexStatusEffect(TEAM_SPIRIT);
                });
            }
        });
    }
}