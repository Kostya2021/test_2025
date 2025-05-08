package de.andrenitze.softpro.domains.skills;

import lombok.Getter;
import lombok.Setter;

public class Skill {
    @Setter @Getter
    private String id;
    private String title;
    private String description;
    private String requiresId;
    private SkillEffect[] effects;
    private int unlockSkillPoints;
    @Setter @Getter
    private boolean unlocked = false;
}