package de.andrenitze.softpro.types;

import lombok.Data;

@Data
public class StatusEffect {
    private StatusEffectType type;
    private float multiplier; // 1.0 = 100%, 0.5 = 50%, 2.25 = 225%, -1.0 = -100%
    private String reason;
    private int cooldown = -1; // How many days the effect lasts (-1 = infinite)

    public StatusEffect(StatusEffectType type, float multiplier, String reason) {
        this.type = type;
        this.multiplier = multiplier;
        this.reason = reason;
    }

    public StatusEffect(StatusEffectType type, float multiplier, String reason, int cooldown) {
        this.type = type;
        this.multiplier = multiplier;
        this.reason = reason;
        this.cooldown = cooldown;
    }

    public void cooldown() {
        if (cooldown > 0) {
            cooldown--;
        }
    }

    public boolean isExpired() {
        return cooldown == 0;
    }
}
