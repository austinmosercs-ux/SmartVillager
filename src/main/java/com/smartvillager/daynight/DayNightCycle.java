package com.smartvillager.daynight;

import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

/**
 * Utility for day/night time queries used across systems.
 *
 * Systems that must stop at night (resource gathering, non-Guard patrol) call
 * isResourceGatheringAllowed() before initiating any out-of-village run.
 * PatrolSystem calls isGuardOnNightDuty() to determine which Guards stay
 * active at night while the others hand off to vanilla sleep behavior.
 */
public final class DayNightCycle {
    private DayNightCycle() {}

    /**
     * True when the level is in the dark/night phase.
     *
     * Uses Level.isDarkOutside() — the Minecraft 26.x replacement for isNight().
     * Returns false in dimensions with fixed time (Nether/End), which is correct
     * because villagers are only managed in the overworld.
     */
    public static boolean isNight(ServerLevel level) {
        return level.isDarkOutside();
    }

    /**
     * False at night — used by Toolsmith, Mason, Fisherman, and Cartographer
     * subroles to gate any out-of-village gathering or scouting run.
     */
    public static boolean isResourceGatheringAllowed(ServerLevel level) {
        return !isNight(level);
    }

    /**
     * Returns true if this Guard should remain on patrol during night.
     *
     * Approximately 1-in-3 Guards are on night rotation, assigned deterministically
     * by UUID hash so the assignment is stable across ticks and sessions.
     * The sole Guard in a village is always on duty so the village is never
     * left completely undefended.
     */
    public static boolean isGuardOnNightDuty(UUID uuid, int totalGuards) {
        if (totalGuards <= 1) return true;
        return Math.abs(uuid.getLeastSignificantBits()) % 3 == 0;
    }
}
