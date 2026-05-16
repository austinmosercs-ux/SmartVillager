package com.smartvillager.health;

/**
 * Per-villager health state, stored as a NeoForge entity attachment.
 *
 * In full simulation, hp is kept in sync with the vanilla entity health via
 * syncFrom() so that combat and environmental damage from mob AI are reflected
 * here without duplicating the vanilla pipeline.
 *
 * In abstract simulation (entity not loaded), hp is tracked independently and
 * updated by HealthSystem.abstractTick() based on starvation from missed meals.
 *
 * The seekingHealing flag signals that this villager needs to path to the
 * Cleric and override their job goal. Brain behaviors (registered in later
 * branches) read this flag to activate the SEEK_HEALING state.
 */
public final class VillagerHealth {

    public static final float MAX = 20.0f;
    /** Below this, the villager sets seekingHealing = true and overrides their job goal. */
    public static final float LOW_HEALTH = 8.0f;
    /**
     * HP lost per tick interval (40 ticks) while a villager is starving in full simulation.
     * 0.5 HP per 40 ticks = ~0.25 HP/second; a full-health villager takes ~80 seconds to die.
     */
    public static final float STARVATION_DAMAGE_PER_TICK_INTERVAL = 0.5f;
    /**
     * HP lost per missed meal in abstract simulation.
     * Each meal covers ~6 hunger points; missing one represents a meaningful starvation period.
     */
    public static final float STARVATION_DAMAGE_PER_MISSED_MEAL = 2.0f;

    private float hp;
    private boolean seekingHealing;

    public VillagerHealth() {
        this.hp = MAX;
    }

    public float get()                { return hp; }
    public boolean isLow()            { return hp < LOW_HEALTH; }
    public boolean isDead()           { return hp <= 0f; }
    public boolean isSeekingHealing() { return seekingHealing; }

    /**
     * Syncs notional HP from the vanilla entity's current health value.
     * Called each tick in full simulation to capture combat and environmental damage
     * that bypasses our system (mob attacks, fall damage, lava, etc.).
     */
    public void syncFrom(float vanillaHp) {
        hp = Math.clamp(vanillaHp, 0f, MAX);
        seekingHealing = hp < LOW_HEALTH;
    }

    /**
     * Applies damage to the notional HP value.
     * Used in abstract simulation (entity not loaded) and for starvation bookkeeping.
     */
    public void takeDamage(float amount) {
        hp = Math.max(0f, hp - amount);
        if (isLow()) seekingHealing = true;
    }

    /** Restores HP. Clears seekingHealing once HP is back above the threshold. */
    public void heal(float amount) {
        hp = Math.min(MAX, hp + amount);
        if (hp >= LOW_HEALTH) seekingHealing = false;
    }
}
