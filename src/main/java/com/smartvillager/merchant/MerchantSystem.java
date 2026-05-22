package com.smartvillager.merchant;

import com.mojang.logging.LogUtils;
import com.smartvillager.SmartVillager;
import com.smartvillager.food.FarmerSystem;
import com.smartvillager.village.SmartVillage;
import com.smartvillager.village.VillageRegistry;
import com.smartvillager.village.VillageStockpile;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.slf4j.Logger;

import java.util.List;
import java.util.Optional;

/**
 * Drives Merchant villager behavior and the dynamic village shop.
 *
 * Primary role — player trade interface:
 *   - During full sim the Merchant paths toward any player within APPROACH_RADIUS.
 *   - When the player right-clicks the Merchant, this system intercepts the event,
 *     builds offers from the current village stockpile, sets them on the Merchant
 *     entity, and allows the vanilla Merchant screen to open.
 *   - Only items with at least MIN_STOCK units in the stockpile appear as offers.
 *   - Prices are dynamic: base price in emeralds / 8; high supply → cheaper, low → pricier.
 *
 * Subrole — supply monitor:
 *   - Periodically scanned by LibrarianCoordinator (unchanged); NEED_RESTOCK posts
 *     are generated there, not here.
 *
 * Abstract simulation:
 *   - No movement; offers are rebuilt on the next full-sim interaction.
 */
@EventBusSubscriber(modid = SmartVillager.MOD_ID)
public final class MerchantSystem {
    private MerchantSystem() {}

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Identifier PROF_MERCHANT =
        Identifier.fromNamespaceAndPath(SmartVillager.MOD_ID, "merchant");

    public static final int TICK_INTERVAL = 40;

    /** Radius within which the Merchant walks toward a player. */
    private static final double APPROACH_RADIUS = 24.0;
    private static final double APPROACH_RADIUS_SQ = APPROACH_RADIUS * APPROACH_RADIUS;
    private static final float APPROACH_SPEED = 0.45f;

    /** Minimum stock level for an item to appear in the shop. */
    private static final int MIN_STOCK = 4;

    /** Stock level considered "high" — triggers a discount. */
    private static final int HIGH_STOCK = 64;

    /** Stock level considered "low" — triggers a surcharge. */
    private static final int LOW_STOCK = 8;

    /** Maximum emerald price per unit to prevent runaway pricing. */
    private static final int MAX_PRICE = 8;

    // Items the Merchant can sell — drawn from whatever the village actually has.
    // Each entry: (item id, units per trade, base emerald price)
    private static final List<TradeTemplate> TRADE_TEMPLATES = List.of(
        new TradeTemplate(Identifier.withDefaultNamespace("bread"),           4, 1),
        new TradeTemplate(Identifier.withDefaultNamespace("cooked_beef"),     2, 2),
        new TradeTemplate(Identifier.withDefaultNamespace("cooked_pork"),     2, 2),
        new TradeTemplate(Identifier.withDefaultNamespace("cooked_chicken"),  2, 2),
        new TradeTemplate(Identifier.withDefaultNamespace("cooked_mutton"),   2, 2),
        new TradeTemplate(Identifier.withDefaultNamespace("cooked_cod"),      3, 1),
        new TradeTemplate(Identifier.withDefaultNamespace("cooked_salmon"),   3, 1),
        new TradeTemplate(Identifier.withDefaultNamespace("iron_ingot"),      4, 3),
        new TradeTemplate(Identifier.withDefaultNamespace("coal"),            8, 1),
        new TradeTemplate(Identifier.withDefaultNamespace("arrow"),           16, 2),
        new TradeTemplate(Identifier.withDefaultNamespace("wool"),            4, 1),
        new TradeTemplate(Identifier.withDefaultNamespace("leather"),         4, 1),
        new TradeTemplate(Identifier.withDefaultNamespace("cobblestone"),     16, 1),
        new TradeTemplate(Identifier.withDefaultNamespace("oak_log"),         8, 1),
        new TradeTemplate(Identifier.withDefaultNamespace("iron_sword"),      1, 8),
        new TradeTemplate(Identifier.withDefaultNamespace("iron_pickaxe"),    1, 6),
        new TradeTemplate(Identifier.withDefaultNamespace("fishing_rod"),     1, 4),
        new TradeTemplate(Identifier.withDefaultNamespace("leather_helmet"),  1, 3),
        new TradeTemplate(Identifier.withDefaultNamespace("leather_chestplate"), 1, 5),
        new TradeTemplate(Identifier.withDefaultNamespace("iron_helmet"),     1, 6),
        new TradeTemplate(Identifier.withDefaultNamespace("iron_chestplate"), 1, 8)
    );

    // -------------------------------------------------------------------------
    // Full simulation — Merchant approaches player
    // -------------------------------------------------------------------------

    public static void tick(ServerLevel level, SmartVillage village, long gameTick) {
        if (gameTick % TICK_INTERVAL != 0) return;

        for (Villager merchant : FarmerSystem.findVillagers(level, village, PROF_MERCHANT)) {
            tickMerchant(level, merchant);
        }
    }

    private static void tickMerchant(ServerLevel level, Villager merchant) {
        BlockPos pos = merchant.blockPosition();
        level.players().stream()
            .min((a, b) -> Double.compare(
                a.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()),
                b.distanceToSqr(pos.getX(), pos.getY(), pos.getZ())))
            .filter(p -> p.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) <= APPROACH_RADIUS_SQ)
            .ifPresent(p -> merchant.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(p.blockPosition(), APPROACH_SPEED, 3)));
    }

    // -------------------------------------------------------------------------
    // Player interaction — open dynamic shop
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public static void onPlayerInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getTarget() instanceof Villager merchant)) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ServerLevel level = (ServerLevel) event.getLevel();
        VillageRegistry registry = VillageRegistry.get(level);

        Optional<SmartVillage> villageOpt = registry.all().stream()
            .filter(v -> v.hasVillager(merchant.getUUID()))
            .findFirst();

        if (villageOpt.isEmpty()) return;

        Identifier profId = villageOpt.get().getRoster().get(merchant.getUUID());
        if (!PROF_MERCHANT.equals(profId)) return;

        // Intercept the interaction — VillagerInteractionHandler would cancel it, but
        // we act first and manually open the vanilla trade screen with dynamic offers.
        event.setCanceled(true);

        MerchantOffers offers = buildOffers(villageOpt.get().getStockpile(), level);
        merchant.setOffers(offers);
        merchant.openTradingScreen(player, merchant.getDisplayName(), 1);

        LOGGER.debug("[SmartVillager] Merchant {} opened shop for player {} ({} offer(s))",
            merchant.getUUID(), player.getScoreboardName(), offers.size());
    }

    // -------------------------------------------------------------------------
    // Offer construction — dynamic prices from stockpile
    // -------------------------------------------------------------------------

    private static MerchantOffers buildOffers(VillageStockpile stockpile, ServerLevel level) {
        MerchantOffers offers = new MerchantOffers();

        for (TradeTemplate t : TRADE_TEMPLATES) {
            int stock = stockpile.getCount(t.item());
            if (stock < MIN_STOCK) continue;

            int price = calculatePrice(t.basePrice(), stock);
            ItemCost cost = new ItemCost(Items.EMERALD, price);

            // Resolve the item from the registry.
            var itemOpt = level.registryAccess()
                .lookup(net.minecraft.core.registries.Registries.ITEM)
                .flatMap(reg -> reg.get(t.item()));
            if (itemOpt.isEmpty()) continue;

            ItemStack result = new ItemStack(itemOpt.get().value(), t.units());

            // maxUses = stock / units (how many trades the village can support right now).
            int maxUses = Math.max(1, stock / t.units());
            offers.add(new MerchantOffer(cost, result, maxUses, 0, 1.0f));
        }

        return offers;
    }

    private static int calculatePrice(int base, int stock) {
        int price = base;
        if (stock >= HIGH_STOCK) price = Math.max(1, price / 2);
        else if (stock <= LOW_STOCK) price = Math.min(MAX_PRICE, price * 2);
        return price;
    }

    // -------------------------------------------------------------------------
    // Abstract simulation
    // -------------------------------------------------------------------------

    public static void abstractTick() {
        // No movement during abstract sim; offers are rebuilt on next interaction.
    }

    // -------------------------------------------------------------------------
    // Trade template record
    // -------------------------------------------------------------------------

    private record TradeTemplate(Identifier item, int units, int basePrice) {}
}
