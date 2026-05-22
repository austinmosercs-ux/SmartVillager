package com.smartvillager.defense;

import com.mojang.logging.LogUtils;
import com.smartvillager.SmartVillager;
import com.smartvillager.needqueue.NeedRequest;
import com.smartvillager.needqueue.NeedTypes;
import com.smartvillager.village.SmartVillage;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages Guard escort missions for villagers heading outside the village boundary.
 *
 * Flow:
 *   1. A villager (Mason, Toolsmith, Cartographer, Fisherman) posts NEED_ESCORT before
 *      heading to a potentially dangerous area.
 *   2. An available Guard (not on threat alert, not already escorting) polls for open
 *      NEED_ESCORT requests and accepts the highest-priority one.
 *   3. During escort the Guard paths to the poster's current position each tick and
 *      engages any threats within ESCORT_THREAT_RADIUS.
 *   4. The escort ends when the poster is back within VILLAGE_BOUNDARY_RADIUS_SQ of the
 *      anchor for at least DONE_WAIT_TICKS consecutive ticks, or when the poster
 *      disappears (died or despawned).
 *   5. The Guard marks the NeedRequest complete and resumes normal patrol.
 *
 * Thread safety: all state maps are accessed only on the server thread.
 */
public final class EscortSystem {
    private EscortSystem() {}

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Identifier PROF_GUARD =
        Identifier.fromNamespaceAndPath(SmartVillager.MOD_ID, "guard");

    public static final int TICK_INTERVAL = 20;

    /** Squared distance from anchor within which a villager is considered "back home". */
    private static final double VILLAGE_BOUNDARY_RADIUS_SQ = 2304.0; // 48 blocks

    /** How long (ticks) the poster must stay near anchor before escort is complete. */
    private static final long DONE_WAIT_TICKS = 60L; // 3 seconds

    /** Radius (blocks) around the Guard to scan for threats during escort. */
    private static final double ESCORT_THREAT_RADIUS = 16.0;

    /** Squared distance at which the Guard stops advancing toward the escorted villager. */
    private static final double ESCORT_FOLLOW_DIST_SQ = 25.0; // 5 blocks

    private static final float ESCORT_SPEED = 0.6f;
    private static final float ENGAGE_SPEED = 0.65f;
    private static final float ATTACK_DAMAGE = 4.0f;
    private static final double MELEE_RANGE_SQ = 4.0;
    private static final int ATTACK_COOLDOWN_TICKS = 20;

    // Server-side runtime state — one entry per actively escorting Guard.
    private static final Map<UUID, UUID> guardToRequestId = new HashMap<>();
    private static final Map<UUID, UUID> guardToEscorted  = new HashMap<>();
    private static final Map<UUID, Long> returnStartTick  = new HashMap<>();
    private static final Map<UUID, Long> lastAttackTick   = new HashMap<>();

    // -------------------------------------------------------------------------
    // Full simulation entry point
    // -------------------------------------------------------------------------

    public static void tick(ServerLevel level, SmartVillage village, long gameTick) {
        if (gameTick % TICK_INTERVAL != 0) return;
        // GuardDefenseSystem takes full control during threat alerts.
        if (village.isThreatAlertActive()) return;

        for (Map.Entry<UUID, Identifier> entry : new HashMap<>(village.getRoster()).entrySet()) {
            if (!PROF_GUARD.equals(entry.getValue())) continue;
            if (!(level.getEntity(entry.getKey()) instanceof Villager guard)) continue;

            UUID guardUUID = guard.getUUID();
            if (guardToEscorted.containsKey(guardUUID)) {
                tickActiveEscort(level, village, guard, gameTick);
            } else {
                tryAcceptEscort(level, village, guard, gameTick);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Accept an open NEED_ESCORT from the queue
    // -------------------------------------------------------------------------

    private static void tryAcceptEscort(ServerLevel level, SmartVillage village,
                                         Villager guard, long gameTick) {
        Optional<NeedRequest> opt = village.getNeedQueue().accept(NeedTypes.NEED_ESCORT, guard.getUUID());
        if (opt.isEmpty()) return;

        NeedRequest request = opt.get();
        UUID posterUUID = request.getPoster();

        if (!(level.getEntity(posterUUID) instanceof Villager)) {
            // Poster already gone — request is stale, cancel it.
            village.getNeedQueue().cancel(request.getId());
            return;
        }

        guardToRequestId.put(guard.getUUID(), request.getId());
        guardToEscorted.put(guard.getUUID(), posterUUID);

        LOGGER.info("[SmartVillager] Guard {} accepted escort for {} in village at {}",
            guard.getUUID(), posterUUID, village.getAnchor());
    }

    // -------------------------------------------------------------------------
    // Active escort tick — follow the poster and protect them
    // -------------------------------------------------------------------------

    private static void tickActiveEscort(ServerLevel level, SmartVillage village,
                                          Villager guard, long gameTick) {
        UUID guardUUID  = guard.getUUID();
        UUID posterUUID = guardToEscorted.get(guardUUID);

        if (!(level.getEntity(posterUUID) instanceof Villager poster)) {
            endEscort(village, guardUUID, "poster disappeared");
            return;
        }

        engageNearbyThreats(level, guard, gameTick);

        if (guard.distanceToSqr(poster) > ESCORT_FOLLOW_DIST_SQ) {
            guard.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(poster.blockPosition(), ESCORT_SPEED, 2));
        }

        checkEscortComplete(village, guard, poster, gameTick);
    }

    private static void engageNearbyThreats(ServerLevel level, Villager guard, long gameTick) {
        AABB area = guard.getBoundingBox().inflate(ESCORT_THREAT_RADIUS);
        List<Monster> threats = level.getEntitiesOfClass(Monster.class, area);
        if (threats.isEmpty()) return;

        Monster target = null;
        double best = Double.MAX_VALUE;
        for (Monster m : threats) {
            double d = guard.distanceToSqr(m);
            if (d < best) { best = d; target = m; }
        }
        if (target == null) return;

        guard.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
            new WalkTarget(target.blockPosition(), ENGAGE_SPEED, 1));

        if (best <= MELEE_RANGE_SQ) {
            long last = lastAttackTick.getOrDefault(guard.getUUID(), 0L);
            if (gameTick - last >= ATTACK_COOLDOWN_TICKS) {
                target.hurtServer(level, level.damageSources().mobAttack(guard), ATTACK_DAMAGE);
                lastAttackTick.put(guard.getUUID(), gameTick);
            }
        }
    }

    private static void checkEscortComplete(SmartVillage village, Villager guard,
                                             Villager poster, long gameTick) {
        UUID guardUUID = guard.getUUID();
        BlockPos anchor = village.getAnchor();
        double distFromAnchorSq = poster.distanceToSqr(
            anchor.getX(), anchor.getY(), anchor.getZ());

        if (distFromAnchorSq <= VILLAGE_BOUNDARY_RADIUS_SQ) {
            if (!returnStartTick.containsKey(guardUUID)) {
                returnStartTick.put(guardUUID, gameTick);
            } else if (gameTick - returnStartTick.get(guardUUID) >= DONE_WAIT_TICKS) {
                endEscort(village, guardUUID, "poster returned home");
            }
        } else {
            returnStartTick.remove(guardUUID);
        }
    }

    // -------------------------------------------------------------------------
    // Escort teardown
    // -------------------------------------------------------------------------

    private static void endEscort(SmartVillage village, UUID guardUUID, String reason) {
        UUID requestId = guardToRequestId.remove(guardUUID);
        guardToEscorted.remove(guardUUID);
        returnStartTick.remove(guardUUID);
        lastAttackTick.remove(guardUUID);

        if (requestId != null) {
            village.getNeedQueue().complete(requestId);
        }

        LOGGER.info("[SmartVillager] Guard {} escort ended ({})", guardUUID, reason);
    }

    // -------------------------------------------------------------------------
    // Query API — used by PatrolSystem to skip guards currently on escort duty
    // -------------------------------------------------------------------------

    public static boolean isEscorting(UUID guardUUID) {
        return guardToEscorted.containsKey(guardUUID);
    }
}
