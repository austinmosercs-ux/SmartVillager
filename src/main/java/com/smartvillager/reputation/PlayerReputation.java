package com.smartvillager.reputation;

/**
 * Reputation constants and tier boundaries for player standing with a village.
 *
 * Reputation is stored as an integer on {@code SmartVillage} (see
 * {@code SmartVillage.getReputation} / {@code SmartVillage.adjustReputation}).
 *
 * Effects by tier:
 *   HOSTILE  (< -50)  — Guards are aggressive on sight; Merchant refuses to trade.
 *   NEUTRAL  (−50–49) — Default; Merchant trades at normal prices; no quests.
 *   FRIENDLY (50–149) — Merchant gives a 10% discount; Librarian offers basic quests.
 *   HONORED  (150+)   — Merchant gives a 25% discount; Librarian offers better quests;
 *                        Guards are less likely to attack even after an accidental hit.
 *
 * Gains:
 *   +10  completing a Librarian quest
 *   + 5  player donation to the stockpile
 *   + 2  Merchant trade completed
 *
 * Losses:
 *   -20  attacking a villager
 *   - 5  ignoring an active NEED_ESCORT (approximate; future implementation)
 */
public final class PlayerReputation {
    private PlayerReputation() {}

    public static final int HOSTILE_THRESHOLD  = -50;
    public static final int FRIENDLY_THRESHOLD =  50;
    public static final int HONORED_THRESHOLD  = 150;

    public static final int GAIN_QUEST_COMPLETE  = 10;
    public static final int GAIN_DONATION        =  5;
    public static final int GAIN_TRADE           =  2;
    public static final int LOSS_ATTACK_VILLAGER = 20;

    public enum Tier { HOSTILE, NEUTRAL, FRIENDLY, HONORED }

    public static Tier tierFor(int reputation) {
        if (reputation < HOSTILE_THRESHOLD)  return Tier.HOSTILE;
        if (reputation >= HONORED_THRESHOLD) return Tier.HONORED;
        if (reputation >= FRIENDLY_THRESHOLD) return Tier.FRIENDLY;
        return Tier.NEUTRAL;
    }

    /** Price multiplier to apply to Merchant offers based on player's reputation tier. */
    public static double priceMultiplierFor(int reputation) {
        return switch (tierFor(reputation)) {
            case HOSTILE   -> 2.0;
            case NEUTRAL   -> 1.0;
            case FRIENDLY  -> 0.9;
            case HONORED   -> 0.75;
        };
    }
}
