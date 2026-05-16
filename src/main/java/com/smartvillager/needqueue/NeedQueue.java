package com.smartvillager.needqueue;

import com.mojang.logging.LogUtils;
import com.smartvillager.SmartVillager;
import com.smartvillager.village.LibrarianCoordinator;
import com.smartvillager.village.SmartVillage;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * System-level NeedQueue logic. Stateless — all mutable state lives in
 * VillageNeedQueue (stored on SmartVillage).
 *
 * Responsibilities:
 *   1. Sync LibrarianCoordinator shortage flags → NeedQueue posts and cancels
 *   2. Expire stale open requests that were never accepted (full sim only)
 *
 * Called from VillageDetector once per tick interval (full sim) and once per
 * abstract batch update.
 */
public final class NeedQueue {
    private NeedQueue() {}

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Poster UUID used for all Librarian-initiated requests.
     * Not a real villager; never appears in the roster.
     */
    public static final UUID LIBRARIAN_POSTER = new UUID(0L, 0L);

    /** Open requests expire after this many ticks if never accepted (~10 minutes). */
    private static final long EXPIRY_TICKS = 12_000L;

    // Healing supply item ID — kept local to avoid importing ClericHealingSystem (circular dep).
    private static final Identifier HEALING_SUPPLY =
        Identifier.fromNamespaceAndPath(SmartVillager.MOD_ID, "healing_supply");

    // Maps each shortage key → (need type, specific item, priority) so the
    // Librarian's shortage flags translate directly into actionable requests.
    private static final Map<Identifier, ShortageMapping> SHORTAGE_TO_REQUEST = Map.ofEntries(
        Map.entry(LibrarianCoordinator.SHORTAGE_FOOD,
            new ShortageMapping(NeedTypes.NEED_RESTOCK,   Identifier.withDefaultNamespace("bread"),         NeedPriority.HIGH)),
        Map.entry(LibrarianCoordinator.SHORTAGE_IRON_ORE,
            new ShortageMapping(NeedTypes.NEED_MATERIALS, Identifier.withDefaultNamespace("iron_ore"),       NeedPriority.NORMAL)),
        Map.entry(LibrarianCoordinator.SHORTAGE_COAL,
            new ShortageMapping(NeedTypes.NEED_MATERIALS, Identifier.withDefaultNamespace("coal"),           NeedPriority.NORMAL)),
        Map.entry(LibrarianCoordinator.SHORTAGE_IRON_INGOT,
            new ShortageMapping(NeedTypes.NEED_MATERIALS, Identifier.withDefaultNamespace("iron_ingot"),     NeedPriority.NORMAL)),
        Map.entry(LibrarianCoordinator.SHORTAGE_ARROWS,
            new ShortageMapping(NeedTypes.NEED_MATERIALS, Identifier.withDefaultNamespace("arrow"),          NeedPriority.NORMAL)),
        Map.entry(LibrarianCoordinator.SHORTAGE_PICKAXE,
            new ShortageMapping(NeedTypes.NEED_TOOLS,     Identifier.withDefaultNamespace("iron_pickaxe"),   NeedPriority.HIGH)),
        Map.entry(LibrarianCoordinator.SHORTAGE_HOE,
            new ShortageMapping(NeedTypes.NEED_TOOLS,     Identifier.withDefaultNamespace("iron_hoe"),       NeedPriority.NORMAL)),
        Map.entry(LibrarianCoordinator.SHORTAGE_AXE,
            new ShortageMapping(NeedTypes.NEED_TOOLS,     Identifier.withDefaultNamespace("iron_axe"),       NeedPriority.NORMAL)),
        Map.entry(LibrarianCoordinator.SHORTAGE_SHOVEL,
            new ShortageMapping(NeedTypes.NEED_TOOLS,     Identifier.withDefaultNamespace("iron_shovel"),    NeedPriority.NORMAL)),
        Map.entry(LibrarianCoordinator.SHORTAGE_FISHING_ROD,
            new ShortageMapping(NeedTypes.NEED_TOOLS,     Identifier.withDefaultNamespace("fishing_rod"),    NeedPriority.NORMAL)),
        Map.entry(LibrarianCoordinator.SHORTAGE_HEALING_SUPPLIES,
            new ShortageMapping(NeedTypes.NEED_MATERIALS, HEALING_SUPPLY,                                    NeedPriority.HIGH))
    );

    // -------------------------------------------------------------------------
    // Full simulation
    // -------------------------------------------------------------------------

    /**
     * Called every tick interval during full simulation, after LibrarianCoordinator.tick().
     * Syncs shortage flags to queue posts and expires stale open requests.
     */
    public static void tick(SmartVillage village, long currentTick) {
        syncShortages(village, currentTick);
        int expired = village.getNeedQueue().expireOlderThan(currentTick, EXPIRY_TICKS);
        if (expired > 0) {
            LOGGER.debug("[SmartVillager] Village at {} — expired {} stale NeedQueue requests",
                village.getAnchor(), expired);
        }
    }

    // -------------------------------------------------------------------------
    // Abstract simulation
    // -------------------------------------------------------------------------

    /**
     * Called during each abstract batch update, after LibrarianCoordinator.abstractTick().
     * Syncs shortages to the queue; no expiry during abstract sim so requests
     * accumulate until full simulation resumes.
     */
    public static void abstractTick(SmartVillage village, long currentTick) {
        syncShortages(village, currentTick);
    }

    // -------------------------------------------------------------------------
    // Shortage → request sync
    // -------------------------------------------------------------------------

    private static void syncShortages(SmartVillage village, long currentTick) {
        VillageNeedQueue queue = village.getNeedQueue();
        for (Map.Entry<Identifier, ShortageMapping> entry : SHORTAGE_TO_REQUEST.entrySet()) {
            ShortageMapping m = entry.getValue();
            Optional<Identifier> itemOpt = Optional.of(m.item());
            boolean isShort = village.getShortages().contains(entry.getKey());
            boolean hasRequest = queue.hasOpenRequest(m.type(), itemOpt);

            if (isShort && !hasRequest) {
                NeedRequest request = NeedRequest.post(m.type(), m.priority(),
                                                       LIBRARIAN_POSTER, itemOpt, currentTick);
                queue.post(request);
                LOGGER.info("[SmartVillager] Village at {} — NeedQueue posted {} for {}",
                    village.getAnchor(), m.type().getPath(), m.item().getPath());
            } else if (!isShort && hasRequest) {
                queue.cancelByTypeAndItem(m.type(), itemOpt);
                LOGGER.info("[SmartVillager] Village at {} — NeedQueue cancelled {} for {} (shortage resolved)",
                    village.getAnchor(), m.type().getPath(), m.item().getPath());
            }
        }
    }

    // -------------------------------------------------------------------------
    // Villager-initiated posting
    // -------------------------------------------------------------------------

    /**
     * Convenience method for villager behavior code to post a request and
     * immediately enqueue it. Returns the posted request.
     */
    public static NeedRequest postRequest(SmartVillage village, Identifier type,
                                          NeedPriority priority, UUID poster,
                                          Optional<Identifier> itemData, long currentTick) {
        NeedRequest request = NeedRequest.post(type, priority, poster, itemData, currentTick);
        village.getNeedQueue().post(request);
        return request;
    }

    // -------------------------------------------------------------------------

    private record ShortageMapping(Identifier type, Identifier item, NeedPriority priority) {}
}
