package com.smartvillager.village;

import com.mojang.logging.LogUtils;
import com.smartvillager.SmartVillager;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Set;

/**
 * Server-side Librarian coordination logic.
 *
 * Called from VillageDetector every 200 ticks (full sim) or on each abstract
 * batch update. Scans the village stockpile against known thresholds, then
 * updates SmartVillage.setShortages() if anything changed.
 *
 * Shortages are runtime-only — not persisted, recomputed on each scan.
 * NeedQueue (branch 7) reads village.getShortages() to post job requests.
 */
public final class LibrarianCoordinator {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String NS = SmartVillager.MOD_ID;

    // --- Shortage key identifiers (read by NeedQueue in branch 7) ---
    public static final Identifier SHORTAGE_FOOD        = Identifier.fromNamespaceAndPath(NS, "shortage/food");
    public static final Identifier SHORTAGE_IRON_ORE    = Identifier.fromNamespaceAndPath(NS, "shortage/iron_ore");
    public static final Identifier SHORTAGE_COAL        = Identifier.fromNamespaceAndPath(NS, "shortage/coal");
    public static final Identifier SHORTAGE_IRON_INGOT  = Identifier.fromNamespaceAndPath(NS, "shortage/iron_ingot");
    public static final Identifier SHORTAGE_ARROWS      = Identifier.fromNamespaceAndPath(NS, "shortage/arrows");
    public static final Identifier SHORTAGE_PICKAXE     = Identifier.fromNamespaceAndPath(NS, "shortage/pickaxe");
    public static final Identifier SHORTAGE_HOE         = Identifier.fromNamespaceAndPath(NS, "shortage/hoe");
    public static final Identifier SHORTAGE_AXE         = Identifier.fromNamespaceAndPath(NS, "shortage/axe");
    public static final Identifier SHORTAGE_SHOVEL      = Identifier.fromNamespaceAndPath(NS, "shortage/shovel");
    public static final Identifier SHORTAGE_FISHING_ROD = Identifier.fromNamespaceAndPath(NS, "shortage/fishing_rod");

    // --- Minimum supply thresholds ---
    private static final int FOOD_MIN        = 32;
    private static final int IRON_ORE_MIN    = 16;
    private static final int COAL_MIN        = 16;
    private static final int IRON_INGOT_MIN  = 8;
    private static final int ARROW_MIN       = 32;
    private static final int PICKAXE_MIN     = 2;
    private static final int HOE_MIN         = 1;
    private static final int AXE_MIN         = 1;
    private static final int SHOVEL_MIN      = 1;
    private static final int FISHING_ROD_MIN = 1;

    // --- Item IDs ---
    public static final Set<Identifier> FOOD_ITEMS = Set.of(
        Identifier.withDefaultNamespace("bread"),
        Identifier.withDefaultNamespace("wheat"),
        Identifier.withDefaultNamespace("carrot"),
        Identifier.withDefaultNamespace("potato"),
        Identifier.withDefaultNamespace("baked_potato"),
        Identifier.withDefaultNamespace("cooked_beef"),
        Identifier.withDefaultNamespace("cooked_porkchop"),
        Identifier.withDefaultNamespace("cooked_chicken"),
        Identifier.withDefaultNamespace("cooked_mutton"),
        Identifier.withDefaultNamespace("cooked_salmon"),
        Identifier.withDefaultNamespace("cooked_cod"),
        Identifier.withDefaultNamespace("beetroot")
    );

    private static final Set<Identifier> IRON_ORE_ITEMS = Set.of(
        Identifier.withDefaultNamespace("iron_ore"),
        Identifier.withDefaultNamespace("deepslate_iron_ore")
    );

    private static final Identifier COAL        = Identifier.withDefaultNamespace("coal");
    private static final Identifier IRON_INGOT  = Identifier.withDefaultNamespace("iron_ingot");
    private static final Identifier ARROW       = Identifier.withDefaultNamespace("arrow");
    private static final Identifier IRON_PICKAXE = Identifier.withDefaultNamespace("iron_pickaxe");
    private static final Identifier IRON_HOE    = Identifier.withDefaultNamespace("iron_hoe");
    private static final Identifier IRON_AXE    = Identifier.withDefaultNamespace("iron_axe");
    private static final Identifier IRON_SHOVEL = Identifier.withDefaultNamespace("iron_shovel");
    private static final Identifier FISHING_ROD = Identifier.withDefaultNamespace("fishing_rod");

    private LibrarianCoordinator() {}

    /** Called during full simulation every LIBRARIAN_CHECK_INTERVAL ticks. */
    public static void tick(SmartVillage village) {
        refreshShortages(village);
    }

    /** Called during abstract batch updates. */
    public static void abstractTick(SmartVillage village) {
        refreshShortages(village);
    }

    private static void refreshShortages(SmartVillage village) {
        Set<Identifier> prev = village.getShortages();
        Set<Identifier> next = detectShortages(village.getStockpile());
        if (!next.equals(prev)) {
            logChanges(village, prev, next);
            village.setShortages(next);
        }
    }

    private static Set<Identifier> detectShortages(VillageStockpile stockpile) {
        Set<Identifier> shortages = new HashSet<>();

        if (stockpile.totalOf(FOOD_ITEMS)     < FOOD_MIN)        shortages.add(SHORTAGE_FOOD);
        if (stockpile.totalOf(IRON_ORE_ITEMS) < IRON_ORE_MIN)    shortages.add(SHORTAGE_IRON_ORE);
        if (stockpile.getCount(COAL)           < COAL_MIN)        shortages.add(SHORTAGE_COAL);
        if (stockpile.getCount(IRON_INGOT)     < IRON_INGOT_MIN)  shortages.add(SHORTAGE_IRON_INGOT);
        if (stockpile.getCount(ARROW)          < ARROW_MIN)       shortages.add(SHORTAGE_ARROWS);
        if (stockpile.getCount(IRON_PICKAXE)   < PICKAXE_MIN)     shortages.add(SHORTAGE_PICKAXE);
        if (stockpile.getCount(IRON_HOE)       < HOE_MIN)         shortages.add(SHORTAGE_HOE);
        if (stockpile.getCount(IRON_AXE)       < AXE_MIN)         shortages.add(SHORTAGE_AXE);
        if (stockpile.getCount(IRON_SHOVEL)    < SHOVEL_MIN)      shortages.add(SHORTAGE_SHOVEL);
        if (stockpile.getCount(FISHING_ROD)    < FISHING_ROD_MIN) shortages.add(SHORTAGE_FISHING_ROD);

        return shortages;
    }

    private static void logChanges(SmartVillage village, Set<Identifier> prev, Set<Identifier> next) {
        for (Identifier id : next) {
            if (!prev.contains(id)) {
                LOGGER.info("[SmartVillager] Village at {} — new shortage: {}",
                    village.getAnchor(), id.getPath());
            }
        }
        for (Identifier id : prev) {
            if (!next.contains(id)) {
                LOGGER.info("[SmartVillager] Village at {} — shortage resolved: {}",
                    village.getAnchor(), id.getPath());
            }
        }
    }
}
