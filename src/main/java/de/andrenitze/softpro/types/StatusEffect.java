package de.andrenitze.softpro.types;

import lombok.Data;

@Data
public class StatusEffect {
    private StatusEffectType type;
    private float multiplier; // 1.0 = 100%, 0.5 = 50%, 2.0 = 200%, -1.0 = -100%
    private String description; // Optional

    public StatusEffect(StatusEffectType type, float multiplier, String description) {
        this.type = type;
        this.multiplier = multiplier;
        this.description = description;
    }
}
