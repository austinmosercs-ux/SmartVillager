package com.smartvillager.personality;

import net.minecraft.util.RandomSource;

/**
 * Per-villager personality attachment. Holds a single {@link PersonalityTrait}
 * assigned randomly at birth and never changed.
 *
 * Registered in {@code ModAttachments.VILLAGER_PERSONALITY}. Access via:
 * {@code villager.getData(ModAttachments.VILLAGER_PERSONALITY)}
 */
public final class VillagerPersonality {

    private PersonalityTrait trait;

    /** Default constructor required by NeoForge attachment builder (no-arg). */
    public VillagerPersonality() {
        this.trait = PersonalityTrait.BRAVE; // placeholder; overwritten on first access if not set
    }

    public VillagerPersonality(PersonalityTrait trait) {
        this.trait = trait;
    }

    public PersonalityTrait getTrait() {
        return trait;
    }

    /**
     * Assign a random trait. Called once when a new villager is registered
     * with the village roster. Weights:
     *   BRAVE     35%
     *   CAUTIOUS  30%
     *   GREEDY    15%
     *   GENEROUS  20%
     */
    public static VillagerPersonality random(RandomSource rng) {
        int roll = rng.nextInt(100);
        PersonalityTrait trait;
        if (roll < 35)       trait = PersonalityTrait.BRAVE;
        else if (roll < 65)  trait = PersonalityTrait.CAUTIOUS;
        else if (roll < 80)  trait = PersonalityTrait.GREEDY;
        else                 trait = PersonalityTrait.GENEROUS;
        return new VillagerPersonality(trait);
    }
}
