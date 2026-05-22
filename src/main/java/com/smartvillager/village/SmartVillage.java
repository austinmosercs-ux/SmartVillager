package com.smartvillager.village;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.smartvillager.build.BuildQueue;
import com.smartvillager.needqueue.VillageNeedQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.item.DyeColor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * All persistent data for a single registered village.
 *
 * Persisted fields (written to disk via VillageRegistry.CODEC):
 *   id               — stable UUID for this village
 *   anchor           — BlockPos of the village Bell (patrol origin for Guards,
 *                      meeting point for all villagers)
 *   villagerTypeId   — Identifier of the VillagerType (desert, plains, etc.);
 *                      determines the Merchant's robe color palette
 *   merchantColor    — DyeColor chosen at registration; fixed for this village
 *   roster           — maps each living villager's entity UUID to the
 *                      Identifier of their assigned profession
 *   lastAbstractUpdate — server game-time tick of the last abstract batch
 *                        update; used to schedule the next one
 *   stockpile        — shared village inventory; all villagers deposit/withdraw
 *                      here; optionalFieldOf so pre-stockpile saves still load
 *
 * Runtime-only fields (NOT persisted):
 *   mode      — SimulationMode; always starts as ABSTRACT on load
 *   shortages — item shortage flags set by LibrarianCoordinator each scan;
 *               recomputed from stockpile so they don't need to be persisted
 */
public final class SmartVillage {

    private static final Codec<Map<UUID, Identifier>> ROSTER_CODEC =
        Codec.unboundedMap(UUIDUtil.STRING_CODEC, Identifier.CODEC);

    public static final Codec<SmartVillage> CODEC = RecordCodecBuilder.create(i -> i.group(
        UUIDUtil.STRING_CODEC
            .fieldOf("id")
            .forGetter(SmartVillage::getId),
        BlockPos.CODEC
            .fieldOf("anchor")
            .forGetter(SmartVillage::getAnchor),
        ResourceKey.<VillagerType>codec(Registries.VILLAGER_TYPE)
            .fieldOf("villager_type")
            .forGetter(SmartVillage::getVillagerTypeKey),
        DyeColor.CODEC
            .fieldOf("merchant_color")
            .forGetter(SmartVillage::getMerchantColor),
        ROSTER_CODEC
            .fieldOf("roster")
            .forGetter(SmartVillage::getRoster),
        Codec.LONG
            .fieldOf("last_abstract_update")
            .forGetter(SmartVillage::getLastAbstractUpdate),
        VillageStockpile.CODEC
            .optionalFieldOf("stockpile")
            .xmap(opt -> opt.orElseGet(VillageStockpile::new), Optional::of)
            .forGetter(SmartVillage::getStockpile),
        VillageNeedQueue.CODEC
            .optionalFieldOf("need_queue")
            .xmap(opt -> opt.orElseGet(VillageNeedQueue::new), Optional::of)
            .forGetter(SmartVillage::getNeedQueue),
        StockpileChestTracker.CODEC
            .optionalFieldOf("chest_tracker")
            .xmap(opt -> opt.orElseGet(StockpileChestTracker::new), Optional::of)
            .forGetter(SmartVillage::getChestTracker),
        UUIDUtil.STRING_CODEC.listOf()
            .optionalFieldOf("golems")
            .<Set<UUID>>xmap(opt -> new HashSet<>(opt.orElseGet(ArrayList::new)),
                             set -> Optional.of(new ArrayList<>(set)))
            .forGetter(SmartVillage::getGolems),
        Codec.LONG
            .optionalFieldOf("golem_replacement_cooldown_tick")
            .xmap(opt -> opt.orElse(0L), Optional::of)
            .forGetter(SmartVillage::getGolemReplacementCooldownTick),
        Codec.INT
            .optionalFieldOf("prosperity_score")
            .xmap(opt -> opt.orElse(0), Optional::of)
            .forGetter(SmartVillage::getProsperityScore),
        BuildQueue.CODEC
            .optionalFieldOf("build_queue")
            .xmap(opt -> opt.orElseGet(BuildQueue::new), Optional::of)
            .forGetter(SmartVillage::getBuildQueue)
    ).apply(i, SmartVillage::new));

    private final UUID id;
    private final BlockPos anchor;
    private final ResourceKey<VillagerType> villagerTypeKey;
    private final DyeColor merchantColor;
    private final Map<UUID, Identifier> roster;
    private long lastAbstractUpdate;
    private final VillageStockpile stockpile;
    private final VillageNeedQueue needQueue;
    private final StockpileChestTracker chestTracker;
    // Persisted: UUIDs of village-owned iron golems currently alive in the world.
    private final Set<UUID> golems;
    // Persisted: earliest game tick at which a replacement golem may be commissioned
    // after a golem slot was vacated. Zero means no cooldown is active.
    private long golemReplacementCooldownTick;
    // Persisted: cumulative prosperity score; drives golem commissioning and future unlocks.
    private int prosperityScore;
    // Persisted: ordered list of build tasks for Mason to execute.
    private final BuildQueue buildQueue;

    private SimulationMode mode = SimulationMode.ABSTRACT;
    private Set<Identifier> shortages = Collections.emptySet();
    // Runtime-only: per-villager notional HP used during abstract simulation.
    // Not persisted — villagers start at full health after a server restart.
    // Serialization will be added alongside attachment persistence in a later branch.
    private final Map<UUID, Float> abstractHealth = new HashMap<>();
    // Runtime-only: notional HP per golem UUID during abstract simulation.
    // Default is GOLEM_MAX_HEALTH (100) so golems load at full health after a restart.
    private final Map<UUID, Integer> abstractGolemHealth = new HashMap<>();

    // Runtime-only threat alert state. Not persisted — resets on server restart.
    // Full sim sets and clears this; abstract sim preserves the last known state.
    private boolean threatAlertActive = false;
    private long lastThreatSeenTick = 0L;
    private long threatAlertStartTick = 0L;

    private static final float DEFAULT_ABSTRACT_HEALTH = 20.0f; // matches VillagerHealth.MAX
    private static final int DEFAULT_ABSTRACT_GOLEM_HEALTH = 100; // matches IronGolemSystem.GOLEM_MAX_HEALTH

    @SuppressWarnings("java:S107") // all parameters are codec-driven fields; no meaningful grouping exists
    public SmartVillage(UUID id, BlockPos anchor, ResourceKey<VillagerType> villagerTypeKey,
                        DyeColor merchantColor, Map<UUID, Identifier> roster,
                        long lastAbstractUpdate, VillageStockpile stockpile,
                        VillageNeedQueue needQueue, StockpileChestTracker chestTracker,
                        Set<UUID> golems, long golemReplacementCooldownTick, int prosperityScore,
                        BuildQueue buildQueue) {
        this.id = id;
        this.anchor = anchor;
        this.villagerTypeKey = villagerTypeKey;
        this.merchantColor = merchantColor;
        this.roster = new HashMap<>(roster);
        this.lastAbstractUpdate = lastAbstractUpdate;
        this.stockpile = stockpile;
        this.needQueue = needQueue;
        this.chestTracker = chestTracker;
        this.golems = new HashSet<>(golems);
        this.golemReplacementCooldownTick = golemReplacementCooldownTick;
        this.prosperityScore = prosperityScore;
        this.buildQueue = buildQueue;
    }

    public static SmartVillage create(BlockPos anchor, ResourceKey<VillagerType> typeKey,
                                      RandomSource random, long gameTime) {
        return new SmartVillage(
            UUID.randomUUID(),
            anchor,
            typeKey,
            MerchantColor.randomFor(typeKey, random),
            new HashMap<>(),
            gameTime,
            new VillageStockpile(),
            new VillageNeedQueue(),
            new StockpileChestTracker(),
            new HashSet<>(),
            0L,
            0,
            new BuildQueue()
        );
    }

    // --- roster management ---

    public boolean hasVillager(UUID villagerUUID) {
        return roster.containsKey(villagerUUID);
    }

    public void assignProfession(UUID villagerUUID, Identifier professionId) {
        roster.put(villagerUUID, professionId);
    }

    public void removeVillager(UUID villagerUUID) {
        roster.remove(villagerUUID);
        abstractHealth.remove(villagerUUID);
    }

    // --- abstract health (runtime-only, used by HealthSystem during abstract simulation) ---

    public float getAbstractHealth(UUID uuid) {
        return abstractHealth.getOrDefault(uuid, DEFAULT_ABSTRACT_HEALTH);
    }

    public void setAbstractHealth(UUID uuid, float hp) {
        abstractHealth.put(uuid, hp);
    }

    public int countProfession(Identifier professionId) {
        int count = 0;
        for (Identifier prof : roster.values()) {
            if (prof.equals(professionId)) count++;
        }
        return count;
    }

    // --- simulation mode ---

    public SimulationMode getMode() {
        return mode;
    }

    public void setMode(SimulationMode mode) {
        this.mode = mode;
    }

    // --- abstract simulation ---

    public long getLastAbstractUpdate() {
        return lastAbstractUpdate;
    }

    public void setLastAbstractUpdate(long tick) {
        this.lastAbstractUpdate = tick;
    }

    // --- stockpile ---

    public VillageStockpile getStockpile() { return stockpile; }

    public StockpileChestTracker getChestTracker() { return chestTracker; }

    /** Called by VillageDetector on ABSTRACT → FULL transition. */
    public void activateFullSim(ServerLevel level) {
        stockpile.activateChests(level, chestTracker);
    }

    /** Called by VillageDetector on FULL → ABSTRACT transition. */
    public void activateAbstractSim(ServerLevel level) {
        stockpile.deactivateChests(level, chestTracker);
    }

    // --- need queue ---

    public VillageNeedQueue getNeedQueue() { return needQueue; }

    // --- shortages (runtime-only, set by LibrarianCoordinator) ---

    public Set<Identifier> getShortages() { return shortages; }

    public void setShortages(Set<Identifier> shortages) {
        this.shortages = Set.copyOf(shortages);
    }

    // --- threat alert (runtime-only, managed by GuardDefenseSystem) ---

    public boolean isThreatAlertActive() { return threatAlertActive; }
    public long getLastThreatSeenTick()  { return lastThreatSeenTick; }
    public long getThreatAlertStartTick(){ return threatAlertStartTick; }

    /** Activates the alert (or refreshes the last-seen tick if already active). */
    public void activateThreatAlert(long currentTick) {
        if (!threatAlertActive) {
            threatAlertStartTick = currentTick;
        }
        threatAlertActive = true;
        lastThreatSeenTick = currentTick;
    }

    public void clearThreatAlert() {
        threatAlertActive = false;
    }

    // --- golem management ---

    /** Returns the set of village-owned golem UUIDs currently alive in the world. */
    public Set<UUID> getGolems() { return Collections.unmodifiableSet(golems); }

    public void addGolem(UUID golemUUID) {
        golems.add(golemUUID);
        abstractGolemHealth.put(golemUUID, DEFAULT_ABSTRACT_GOLEM_HEALTH);
    }

    /**
     * Removes a golem UUID from the tracked set and starts the replacement cooldown.
     *
     * @param golemUUID  the dead golem's UUID
     * @param currentTick the game tick at the time of death
     */
    public void removeGolem(UUID golemUUID, long currentTick) {
        golems.remove(golemUUID);
        abstractGolemHealth.remove(golemUUID);
        // Block replacement for one in-game day after death.
        golemReplacementCooldownTick = currentTick + 24000L;
    }

    /** Maximum village-owned golems: one per Bell. Currently always 1 (single-Bell villages). */
    public static final int GOLEM_CAP = 1;

    public long getGolemReplacementCooldownTick() { return golemReplacementCooldownTick; }

    // --- abstract golem health ---

    public int getAbstractGolemHealth(UUID golemUUID) {
        return abstractGolemHealth.getOrDefault(golemUUID, DEFAULT_ABSTRACT_GOLEM_HEALTH);
    }

    public void setAbstractGolemHealth(UUID golemUUID, int hp) {
        abstractGolemHealth.put(golemUUID, hp);
    }

    // --- prosperity ---

    public int getProsperityScore() { return prosperityScore; }

    public void addProsperity(int amount) {
        prosperityScore += amount;
    }

    // --- build queue ---

    public BuildQueue getBuildQueue() { return buildQueue; }

    // --- getters ---

    public UUID getId()                          { return id; }
    public BlockPos getAnchor()                  { return anchor; }
    public ResourceKey<VillagerType> getVillagerTypeKey() { return villagerTypeKey; }
    public DyeColor getMerchantColor()           { return merchantColor; }
    public Map<UUID, Identifier> getRoster()     { return roster; }
}
