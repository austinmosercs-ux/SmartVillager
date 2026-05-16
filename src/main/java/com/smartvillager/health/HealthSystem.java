package com.smartvillager.health;

import com.smartvillager.registration.ModAttachments;
import com.smartvillager.village.SmartVillage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.AABB;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Central health logic for the village simulation.
 *
 * Full simulation: called each tick interval after HungerSystem.tick(). Syncs
 * notional HP from the vanilla entity (picking up combat and environmental
 * damage for free), then applies starvation damage to any villager that the
 * hunger system could not feed.
 *
 * Abstract simulation: takes the per-villager missed-meal count returned by
 * HungerSystem.abstractTick() and applies starvation damage to the notional HP
 * stored in SmartVillage. Returns the UUIDs of villagers whose HP reached zero
 * so the caller can remove them from the roster.
 *
 * Death removal for loaded villagers is handled by VillageDetector via
 * LivingDeathEvent, which covers all damage types (combat, environment,
 * starvation) without any special-casing here.
 */
public final class HealthSystem {
    private HealthSystem() {}

    private static final double SCAN_RADIUS = 192.0;

    // -------------------------------------------------------------------------
    // Full simulation
    // -------------------------------------------------------------------------

    /**
     * Must be called after HungerSystem.tick() so hunger state is current.
     * A villager still hungry after the hunger tick could not eat — apply starvation.
     */
    public static void tick(ServerLevel level, SmartVillage village) {
        BlockPos anchor = village.getAnchor();
        double r = SCAN_RADIUS;
        AABB area = new AABB(
            anchor.getX() - r, anchor.getY() - 64.0, anchor.getZ() - r,
            anchor.getX() + r, anchor.getY() + 64.0, anchor.getZ() + r
        );

        List<Villager> nearby = level.getEntitiesOfClass(Villager.class, area);
        for (Villager villager : nearby) {
            if (!village.hasVillager(villager.getUUID())) continue;
            tickVillager(level, villager);
        }
    }

    private static void tickVillager(ServerLevel level, Villager villager) {
        VillagerHealth health = villager.getData(ModAttachments.VILLAGER_HEALTH);

        // Sync from vanilla HP to capture mob attacks, fall damage, lava, etc.
        health.syncFrom(villager.getHealth());

        // If still hungry after the hunger tick, the stockpile had nothing — starvation.
        if (villager.getData(ModAttachments.VILLAGER_HUNGER).isHungry()) {
            villager.hurtServer(
                level,
                level.damageSources().starve(),
                VillagerHealth.STARVATION_DAMAGE_PER_TICK_INTERVAL
            );
            // Sync again so our notional HP reflects the damage we just dealt.
            health.syncFrom(villager.getHealth());
        }
        // seekingHealing is updated by syncFrom based on the current HP threshold.
        // Brain behaviors (future branches) check health.isSeekingHealing() to
        // override job goals and path to the Cleric.
    }

    // -------------------------------------------------------------------------
    // Abstract simulation
    // -------------------------------------------------------------------------

    /**
     * Applies starvation damage to villagers based on missed meals reported by
     * HungerSystem.abstractTick() and returns the UUIDs of any that died.
     *
     * Callers are responsible for removing dead UUIDs from the village roster.
     *
     * @param missedMealsByVillager map of UUID → count of meals the stockpile could not cover
     * @return UUIDs of villagers whose notional HP reached zero this batch
     */
    public static Set<UUID> abstractTick(SmartVillage village, Map<UUID, Integer> missedMealsByVillager) {
        Set<UUID> died = new HashSet<>();
        for (Map.Entry<UUID, Integer> entry : missedMealsByVillager.entrySet()) {
            UUID uuid = entry.getKey();
            float damage = entry.getValue() * VillagerHealth.STARVATION_DAMAGE_PER_MISSED_MEAL;
            float hp = Math.max(0f, village.getAbstractHealth(uuid) - damage);
            village.setAbstractHealth(uuid, hp);
            if (hp <= 0f) {
                died.add(uuid);
            }
        }
        return died;
    }
}
