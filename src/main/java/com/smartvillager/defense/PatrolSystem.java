package com.smartvillager.defense;

import com.smartvillager.SmartVillager;
import com.smartvillager.daynight.DayNightCycle;
import com.smartvillager.village.SmartVillage;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Drives Guard perimeter patrol during periods of no threat.
 *
 * Generates 8 waypoints evenly spaced around the village Bell anchor at
 * PATROL_RADIUS blocks. Each Guard's starting waypoint is offset by their
 * UUID hash so multiple Guards spread across the perimeter rather than
 * bunching at the same point.
 *
 * Each tick interval the system checks whether a Guard is close enough to
 * their current waypoint to advance; if so it increments their index and
 * sets WALK_TARGET to the next waypoint. The patrol halts automatically
 * when the village THREAT_ALERT is active — GuardDefenseSystem takes over.
 */
public final class PatrolSystem {
    private PatrolSystem() {}

    private static final Identifier PROF_GUARD =
        Identifier.fromNamespaceAndPath(SmartVillager.MOD_ID, "guard");

    /** How often (ticks) the patrol system updates during full simulation. */
    public static final int TICK_INTERVAL = 20;

    /** Distance (blocks) from the Bell anchor to patrol waypoints. */
    private static final int PATROL_RADIUS = 16;

    /** Squared distance (blocks²) at which a Guard is considered "at" their waypoint. */
    private static final double WAYPOINT_REACH_DIST_SQ = 9.0;

    /** Guard movement speed during routine patrol. */
    private static final float PATROL_SPEED = 0.5f;

    /** Tracks which waypoint index each Guard is currently heading toward. */
    private static final Map<UUID, Integer> waypointIndices = new HashMap<>();

    // -------------------------------------------------------------------------
    // Full simulation entry point
    // -------------------------------------------------------------------------

    public static void tick(ServerLevel level, SmartVillage village, long gameTick) {
        if (gameTick % TICK_INTERVAL != 0) return;
        if (village.isThreatAlertActive()) return;

        // Count guards first so night-duty threshold is computed correctly.
        int totalGuards = (int) village.getRoster().values().stream()
            .filter(PROF_GUARD::equals)
            .count();

        boolean night = DayNightCycle.isNight(level);
        List<BlockPos> waypoints = generateWaypoints(village.getAnchor());

        village.getRoster().entrySet().stream()
            .filter(e -> PROF_GUARD.equals(e.getValue()))
            .map(e -> level.getEntity(e.getKey()))
            .filter(Villager.class::isInstance)
            .map(e -> (Villager) e)
            // Off-duty guards skip patrol at night — vanilla sleep behavior takes over.
            .filter(guard -> !night || DayNightCycle.isGuardOnNightDuty(guard.getUUID(), totalGuards))
            .forEach(guard -> tickGuardPatrol(guard, waypoints));
    }

    // -------------------------------------------------------------------------
    // Per-Guard patrol logic
    // -------------------------------------------------------------------------

    private static void tickGuardPatrol(Villager guard, List<BlockPos> waypoints) {
        int index = waypointIndices.computeIfAbsent(
            guard.getUUID(),
            uuid -> Math.abs(uuid.hashCode()) % waypoints.size()
        );

        BlockPos target = waypoints.get(index);

        if (guard.blockPosition().distSqr(target) <= WAYPOINT_REACH_DIST_SQ) {
            index = (index + 1) % waypoints.size();
            waypointIndices.put(guard.getUUID(), index);
            target = waypoints.get(index);
        }

        guard.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
            new WalkTarget(target, PATROL_SPEED, 2));
    }

    // -------------------------------------------------------------------------
    // Waypoint generation
    // -------------------------------------------------------------------------

    /**
     * Returns 8 patrol waypoints distributed at PATROL_RADIUS blocks around the anchor.
     * Points are at the anchor's Y level; the pathfinder adjusts for terrain.
     */
    static List<BlockPos> generateWaypoints(BlockPos anchor) {
        List<BlockPos> waypoints = new ArrayList<>(8);
        for (int i = 0; i < 8; i++) {
            double angle = i * (Math.PI / 4.0);
            int dx = (int) Math.round(PATROL_RADIUS * Math.cos(angle));
            int dz = (int) Math.round(PATROL_RADIUS * Math.sin(angle));
            waypoints.add(anchor.offset(dx, 0, dz));
        }
        return waypoints;
    }
}
