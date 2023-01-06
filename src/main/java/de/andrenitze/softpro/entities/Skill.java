package de.andrenitze.softpro.entities;

public class Skill {
    private String id;
    private String title;
    private String description;
    private String requiresId;
    private SkillEffect[] effects;
    private int unlockSkillPoints;
    private boolean unlocked = false;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public boolean isUnlocked() {
        return unlocked;
    }

    public void setUnlocked(boolean unlocked) {
        this.unlocked = unlocked;
    }
}
