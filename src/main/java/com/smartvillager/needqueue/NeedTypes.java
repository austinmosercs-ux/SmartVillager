package com.smartvillager.needqueue;

import com.smartvillager.SmartVillager;
import net.minecraft.resources.Identifier;

/**
 * All NeedRequest type identifiers used by the village communication system.
 *
 * Type identifiers are namespaced under smartvillager:need/. The NeedRequest.itemData()
 * field carries the specific item or category for request types that target a resource
 * (NEED_MATERIALS, NEED_TOOLS, NEED_RESTOCK, WANT_TO_BUY).
 */
public final class NeedTypes {
    private NeedTypes() {}

    private static Identifier of(String path) {
        return Identifier.fromNamespaceAndPath(SmartVillager.MOD_ID, "need/" + path);
    }

    /** Guard: escort a villager heading outside safe village boundaries and back. */
    public static final Identifier NEED_ESCORT = of("escort");

    /** Cleric: path to and heal the posting villager. */
    public static final Identifier NEED_HEALING = of("healing");

    /**
     * Toolsmith: craft tools for a villager that has none.
     * itemData = the specific tool needed (e.g. minecraft:iron_pickaxe).
     */
    public static final Identifier NEED_TOOLS = of("tools");

    /**
     * Any producer: deposit specific materials to the shared stockpile.
     * itemData = the item that is running low (e.g. minecraft:iron_ore).
     */
    public static final Identifier NEED_MATERIALS = of("materials");

    /**
     * Butcher → Shepherd: increase animal culling rate — food supply is critical.
     * No itemData needed; implies all food-producing animals.
     */
    public static final Identifier NEED_FOOD_BOOST = of("food_boost");

    /**
     * Merchant or Librarian → relevant producer: restock a specific item type.
     * itemData = the item or category that needs restocking.
     */
    public static final Identifier NEED_RESTOCK = of("restock");

    /**
     * Weaponsmith / Armorer: acquire iron ingots by any means (smelt, request, trade).
     * itemData = minecraft:iron_ingot.
     */
    public static final Identifier WANT_TO_BUY = of("want_to_buy");
}
