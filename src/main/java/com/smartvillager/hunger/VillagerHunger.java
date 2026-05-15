package com.smartvillager.hunger;

/**
 * Per-villager hunger value, stored as a NeoForge entity attachment.
 *
 * Range: 0.0 (starving) to MAX (full). Depletes over time at a rate determined
 * by the villager's profession. When hunger drops below HUNGRY the villager
 * attempts to eat from the village stockpile. Health damage when the stockpile
 * is empty is handled by the health system (branch 6).
 */
public final class VillagerHunger {

    public static final float MAX    = 20.0f;
    /** Below this, the villager needs to eat from the stockpile. */
    public static final float HUNGRY = 6.0f;
    /** Hunger restored by consuming one food item from the stockpile. */
    public static final float HUNGER_PER_MEAL = 6.0f;

    private float value;

    public VillagerHunger() {
        this.value = MAX;
    }

    public float get()        { return value; }
    public boolean isHungry() { return value < HUNGRY; }

    public void deplete(float amount) {
        value = Math.max(0f, value - amount);
    }

    public void restore(float amount) {
        value = Math.min(MAX, value + amount);
    }
}
