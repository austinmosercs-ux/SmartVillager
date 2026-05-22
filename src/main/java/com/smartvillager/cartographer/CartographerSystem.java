package com.smartvillager.cartographer;

import com.mojang.logging.LogUtils;
import com.smartvillager.build.BuildQueue;
import com.smartvillager.build.BuildTask;
import com.smartvillager.build.BuildTaskType;
import com.smartvillager.daynight.DayNightCycle;
import com.smartvillager.food.FarmerSystem;
import com.smartvillager.needqueue.NeedPriority;
import com.smartvillager.needqueue.NeedQueue;
import com.smartvillager.needqueue.NeedTypes;
import com.smartvillager.village.SmartVillage;
import com.smartvillager.village.StockpileChestTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Drives Cartographer behavior in both simulation modes.
 *
 * Primary role — map the village and plan build sites:
 *   - Walks survey waypoints around the village to "survey" the boundary.
 *   - Checks village state (population, prosperity, chest count) and enqueues
 *     PLACE_CHEST tasks when more storage is needed.
 *   - Enqueues future structure types as the village grows (currently PLACE_CHEST;
 *     house and farm placement will follow once those BuildTaskTypes are added).
 *
 * Scouting subrole — explore outside the village:
 *   - Posts NEED_ESCORT before leaving the anchor vicinity.
 *   - Walks to SCOUT_RADIUS positions around the anchor to map new territory.
 *   - Returns to anchor when scouting is done.
 *
 * Abstract simulation:
 *   - Queues a PLACE_CHEST task if conditions are met (chest expansion check).
 *   - No physical movement; scouting is deferred until full sim resumes.
 */
public final class CartographerSystem {
    private CartographerSystem() {}

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Identifier PROF_CARTOGRAPHER =
        Identifier.withDefaultNamespace("cartographer");

    public static final int TICK_INTERVAL = 20;

    // --- Survey (primary role) ---
    private static final int  SURVEY_RADIUS            = 20;
    private static final long SURVEY_INTERVAL          = 400L;   // every ~20 s
    private static final double SURVEY_REACH_SQ        = 16.0;

    // --- Scouting subrole ---
    private static final int  SCOUT_RADIUS             = 64;     // blocks from anchor
    private static final long SCOUT_INTERVAL           = 6000L;  // every ~5 min
    private static final long SCOUT_DURATION           = 600L;   // stay out 30 s then return
    private static final double SCOUT_REACH_SQ         = 36.0;

    // --- Build planning ---
    private static final long BUILD_PLAN_INTERVAL      = 1200L;  // every minute
    private static final int  EXTRA_CHEST_PROSPERITY   = 150;    // plan extra chest at this threshold
    private static final int  CHEST_BUILD_TICKS        = 200;

    // Per-Cartographer state.
    private static final Map<UUID, Integer> surveyIndex      = new HashMap<>();
    private static final Map<UUID, Long>    lastSurveyTick   = new HashMap<>();
    private static final Map<UUID, Long>    lastScoutTick    = new HashMap<>();
    private static final Map<UUID, Long>    scoutStartTick   = new HashMap<>();
    private static final Map<UUID, BlockPos> scoutTarget     = new HashMap<>();
    private static final Map<UUID, Long>    lastBuildPlanTick = new HashMap<>();

    // -------------------------------------------------------------------------
    // Full simulation entry point
    // -------------------------------------------------------------------------

    public static void tick(ServerLevel level, SmartVillage village, long gameTick) {
        if (gameTick % TICK_INTERVAL != 0) return;
        if (!DayNightCycle.isResourceGatheringAllowed(level)) return;

        for (Villager carto : FarmerSystem.findVillagers(level, village, PROF_CARTOGRAPHER)) {
            tickCartographer(level, village, carto, gameTick);
        }
    }

    private static void tickCartographer(ServerLevel level, SmartVillage village,
                                          Villager carto, long gameTick) {
        UUID id = carto.getUUID();

        maybeRunBuildPlanning(level, village, gameTick, id);

        if (scoutTarget.containsKey(id)) {
            tickScouting(level, village, carto, gameTick);
        } else if (maybeStartScout(level, village, carto, gameTick)) {
            // Scout just started; movement will begin next tick.
        } else {
            tickSurvey(village, carto, gameTick);
        }
    }

    // -------------------------------------------------------------------------
    // Survey — walk waypoints around the village boundary
    // -------------------------------------------------------------------------

    private static void tickSurvey(SmartVillage village, Villager carto, long gameTick) {
        UUID id = carto.getUUID();
        long last = lastSurveyTick.getOrDefault(id, 0L);
        if (gameTick - last < SURVEY_INTERVAL) return;

        BlockPos[] waypoints = surveyWaypoints(village.getAnchor());
        int idx = surveyIndex.getOrDefault(id, 0);
        BlockPos target = waypoints[idx];

        if (carto.distanceToSqr(target.getX(), target.getY(), target.getZ()) <= SURVEY_REACH_SQ) {
            idx = (idx + 1) % waypoints.length;
            surveyIndex.put(id, idx);
            lastSurveyTick.put(id, gameTick);
            target = waypoints[idx];
        }

        carto.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
            new WalkTarget(target, 0.5f, 2));
    }

    private static BlockPos[] surveyWaypoints(BlockPos anchor) {
        BlockPos[] pts = new BlockPos[8];
        for (int i = 0; i < 8; i++) {
            double angle = i * (Math.PI / 4.0);
            int dx = (int) Math.round(SURVEY_RADIUS * Math.cos(angle));
            int dz = (int) Math.round(SURVEY_RADIUS * Math.sin(angle));
            pts[i] = anchor.offset(dx, 0, dz);
        }
        return pts;
    }

    // -------------------------------------------------------------------------
    // Scouting subrole — venture outside the village boundary
    // -------------------------------------------------------------------------

    private static boolean maybeStartScout(ServerLevel level, SmartVillage village,
                                            Villager carto, long gameTick) {
        UUID id = carto.getUUID();
        long last = lastScoutTick.getOrDefault(id, 0L);
        if (gameTick - last < SCOUT_INTERVAL) return false;

        // Post NEED_ESCORT before leaving; one Guard must accompany the Cartographer.
        if (!village.getNeedQueue().hasOpenRequest(NeedTypes.NEED_ESCORT, Optional.empty())) {
            NeedQueue.postRequest(village, NeedTypes.NEED_ESCORT, NeedPriority.NORMAL,
                id, Optional.empty(), gameTick);
            LOGGER.info("[SmartVillager] Cartographer {} posted NEED_ESCORT before scouting in village {}",
                id, village.getAnchor());
        }

        // Pick a random cardinal scout target outside the boundary.
        BlockPos target = pickScoutTarget(village.getAnchor(), level.getRandom().nextInt(4));
        scoutTarget.put(id, target);
        scoutStartTick.put(id, gameTick);
        lastScoutTick.put(id, gameTick);

        LOGGER.debug("[SmartVillager] Cartographer {} starting scout run to {} (village {})",
            id, target, village.getAnchor());
        return true;
    }

    private static void tickScouting(ServerLevel level, SmartVillage village,
                                      Villager carto, long gameTick) {
        UUID id = carto.getUUID();
        BlockPos target = scoutTarget.get(id);
        long startTick = scoutStartTick.getOrDefault(id, gameTick);

        boolean atTarget = carto.distanceToSqr(
            target.getX(), target.getY(), target.getZ()) <= SCOUT_REACH_SQ;
        boolean timeUp = (gameTick - startTick) >= SCOUT_DURATION;

        if (timeUp || atTarget) {
            // Scout complete — return to anchor.
            scoutTarget.remove(id);
            scoutStartTick.remove(id);
            carto.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(village.getAnchor(), 0.5f, 2));
            LOGGER.debug("[SmartVillager] Cartographer {} finished scout, returning to village {}",
                id, village.getAnchor());
        } else {
            carto.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(target, 0.55f, 3));
        }
    }

    private static BlockPos pickScoutTarget(BlockPos anchor, int direction) {
        return switch (direction) {
            case 0 -> anchor.offset(SCOUT_RADIUS, 0, 0);
            case 1 -> anchor.offset(-SCOUT_RADIUS, 0, 0);
            case 2 -> anchor.offset(0, 0, SCOUT_RADIUS);
            default -> anchor.offset(0, 0, -SCOUT_RADIUS);
        };
    }

    // -------------------------------------------------------------------------
    // Build planning — enqueue structure tasks for Mason
    // -------------------------------------------------------------------------

    private static void maybeRunBuildPlanning(ServerLevel level, SmartVillage village,
                                               long gameTick, UUID id) {
        long last = lastBuildPlanTick.getOrDefault(id, 0L);
        if (gameTick - last < BUILD_PLAN_INTERVAL) return;
        lastBuildPlanTick.put(id, gameTick);

        planExtraChest(level, village);
    }

    /**
     * Queues a PLACE_CHEST expansion task when:
     *   - Prosperity is at or above EXTRA_CHEST_PROSPERITY
     *   - No PLACE_CHEST is already queued
     *   - A valid placement position can be found near the anchor
     */
    private static void planExtraChest(ServerLevel level, SmartVillage village) {
        if (village.getProsperityScore() < EXTRA_CHEST_PROSPERITY) return;
        BuildQueue bq = village.getBuildQueue();
        if (bq.hasTaskOfType(BuildTaskType.PLACE_CHEST)) return;

        BlockPos target = findExpansionPos(level, village.getAnchor(),
                                           village.getChestTracker());
        if (target == null) return;

        bq.enqueue(new BuildTask(BuildTaskType.PLACE_CHEST, target, CHEST_BUILD_TICKS, 0));
        LOGGER.info("[SmartVillager] Cartographer planned storehouse expansion at {} (village {})",
            target, village.getAnchor());
    }

    private static BlockPos findExpansionPos(ServerLevel level, BlockPos anchor,
                                              StockpileChestTracker tracker) {
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    BlockPos candidate = anchor.offset(dx, dy, dz);
                    if (tracker.positions().contains(candidate)) continue;
                    if (!level.getBlockState(candidate).isAir()) continue;
                    BlockPos floor = candidate.below();
                    if (level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Abstract simulation
    // -------------------------------------------------------------------------

    public static void abstractTick(SmartVillage village) {
        // Physical scouting requires full sim; clear any in-progress scout targets
        // so the Cartographer starts fresh when full sim resumes.
        // Build planning runs independently — call the same check but without level access.
        if (village.countProfession(PROF_CARTOGRAPHER) == 0) return;

        // Queue a chest expansion if conditions are met (no level needed for the check).
        if (village.getProsperityScore() >= EXTRA_CHEST_PROSPERITY
                && !village.getBuildQueue().hasTaskOfType(BuildTaskType.PLACE_CHEST)) {
            // Record intent; Mason will execute the actual placement in full sim.
            // We cannot find a valid BlockPos without level access, so this is a no-op —
            // the planning run will fire again when full simulation resumes.
            LOGGER.debug("[SmartVillager] Abstract Cartographer: chest expansion pending (will plan on full sim resume) village {}",
                village.getAnchor());
        }
    }
}
