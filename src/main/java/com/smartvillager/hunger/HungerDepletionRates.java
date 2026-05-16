package com.smartvillager.hunger;

import com.smartvillager.SmartVillager;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps each villager profession to its hunger depletion rate.
 *
 * The rate is the number of server ticks between each 1-point hunger depletion.
 * A lower number means hunger drops faster.
 *
 * Guard uses the SmartVillager namespace; all others are vanilla ("minecraft").
 * Merchant uses the SmartVillager namespace (custom profession).
 * Nitwit is not listed — it falls through to NORMAL, which is intentional:
 * it drains food at the same rate as productive villagers while contributing nothing.
 */
public final class HungerDepletionRates {
    private HungerDepletionRates() {}

    /** Physically demanding roles: Guard, Toolsmith, Mason. */
    public static final float FAST   = 200f;
    /** Standard rate for most professions. */
    public static final float NORMAL = 300f;
    /** Sedentary / administrative roles: Librarian, Merchant. */
    public static final float SLOW   = 450f;

    private static final String MC = "minecraft";
    private static final Map<Identifier, Float> RATES = new HashMap<>();

    static {
        // Fast (physical / active work)
        put(SmartVillager.MOD_ID, "guard",    FAST);
        put(MC,                   "toolsmith", FAST);
        put(MC,                   "mason",     FAST);
        // Slow (administrative / sedentary)
        put(MC,                   "librarian", SLOW);
        put(SmartVillager.MOD_ID, "merchant",  SLOW);
        // Everything else (Farmer, Fisherman, Shepherd, Butcher, Leatherworker,
        // Fletcher, Weaponsmith, Armorer, Cartographer, Cleric, Nitwit) → NORMAL
    }

    private static void put(String namespace, String path, float rate) {
        RATES.put(Identifier.fromNamespaceAndPath(namespace, path), rate);
    }

    /** Returns the ticks-per-hunger-point rate for the given profession. */
    public static float rateFor(Identifier professionId) {
        return RATES.getOrDefault(professionId, NORMAL);
    }
}
