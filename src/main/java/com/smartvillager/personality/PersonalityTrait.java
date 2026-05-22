package com.smartvillager.personality;

import com.mojang.serialization.Codec;

/**
 * Simple personality traits that bias a villager's NeedQueue decisions
 * and tolerance for hunger/health risk.
 *
 * Assigned once at birth (via VillagerPersonality) and never changed.
 * Each villager has exactly one trait.
 */
public enum PersonalityTrait {

    /** Willing to act without an escort, tolerates longer hunger/health windows. */
    BRAVE,

    /** Always requests escort before venturing out; retreats to safety sooner. */
    CAUTIOUS,

    /** Slower to respond to NeedQueue requests; charges more as a Merchant. */
    GREEDY,

    /** Responds faster to NeedQueue requests; shares resources more readily. */
    GENEROUS;

    public static final Codec<PersonalityTrait> CODEC =
        Codec.STRING.xmap(PersonalityTrait::valueOf, PersonalityTrait::name);

    // -------------------------------------------------------------------------
    // Behaviour modifiers used by other systems
    // -------------------------------------------------------------------------

    /**
     * Likelihood (0.0 – 1.0) that this villager posts NEED_ESCORT before
     * a resource run. BRAVE villagers sometimes skip it; CAUTIOUS always post.
     */
    public double escortRequestChance() {
        return switch (this) {
            case BRAVE    -> 0.25;
            case CAUTIOUS -> 1.00;
            case GREEDY   -> 0.60;
            case GENEROUS -> 0.70;
        };
    }

    /**
     * Fraction of the normal NeedQueue poll interval applied to this villager.
     * Values below 1.0 mean the villager checks the queue more often (faster
     * response); above 1.0 means less often (slower response).
     */
    public double queueResponseMultiplier() {
        return switch (this) {
            case BRAVE    -> 1.0;
            case CAUTIOUS -> 1.0;
            case GREEDY   -> 1.5;
            case GENEROUS -> 0.7;
        };
    }

    /**
     * Merchant price multiplier applied on top of the stockpile-based dynamic
     * price. Values above 1.0 inflate prices; below 1.0 discount them.
     */
    public double merchantPriceMultiplier() {
        return switch (this) {
            case BRAVE    -> 1.0;
            case CAUTIOUS -> 1.0;
            case GREEDY   -> 1.5;
            case GENEROUS -> 0.8;
        };
    }
}
