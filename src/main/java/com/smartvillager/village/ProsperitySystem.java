package com.smartvillager.village;

import com.mojang.logging.LogUtils;
import com.smartvillager.SmartVillager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

/**
 * Event-driven prosperity adjustments. Passive time-based accrual is handled
 * in VillageDetector (village.addProsperity(1) each Librarian check interval).
 * This class hooks discrete events that warrant larger one-time changes.
 *
 * Increases (single event):
 *   +20  — threat defeated (tracked externally via onThreatDefeated)
 *   +15  — structure built (tracked via onStructureBuilt)
 *   +10  — successful Merchant trade (tracked via onMerchantTrade)
 *   + 5  — player donation to stockpile chest
 *
 * Decreases (single event):
 *   -30  — villager death
 *   - 5  — villager starvation in progress (per abstract batch; see HealthSystem hook)
 *
 * Prosperity is bounded below at 0; it never goes negative.
 */
@EventBusSubscriber(modid = SmartVillager.MOD_ID)
public final class ProsperitySystem {
    private ProsperitySystem() {}

    private static final Logger LOGGER = LogUtils.getLogger();

    // --- Change magnitudes ---
    public static final int GAIN_THREAT_DEFEATED  = 20;
    public static final int GAIN_STRUCTURE_BUILT  = 15;
    public static final int GAIN_TRADE_COMPLETED  = 10;
    public static final int GAIN_PLAYER_DONATION  =  5;
    public static final int LOSS_VILLAGER_DEATH   = 30;
    public static final int LOSS_STARVATION_TICK  =  5;

    // -------------------------------------------------------------------------
    // Public API — called by other systems on key events
    // -------------------------------------------------------------------------

    public static void onThreatDefeated(SmartVillage village) {
        adjust(village, GAIN_THREAT_DEFEATED, "threat defeated");
    }

    public static void onStructureBuilt(SmartVillage village) {
        adjust(village, GAIN_STRUCTURE_BUILT, "structure built");
    }

    public static void onTradeCompleted(SmartVillage village) {
        adjust(village, GAIN_TRADE_COMPLETED, "Merchant trade");
    }

    public static void onPlayerDonation(SmartVillage village) {
        adjust(village, GAIN_PLAYER_DONATION, "player donation");
    }

    public static void onVillagerStarving(SmartVillage village) {
        adjust(village, -LOSS_STARVATION_TICK, "starvation in progress");
    }

    // -------------------------------------------------------------------------
    // Event hook — villager death (negative prosperity)
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public static void onVillagerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) return;
        if (villager.level().isClientSide()) return;

        ServerLevel level = (ServerLevel) villager.level();
        VillageRegistry.get(level).all().stream()
            .filter(v -> v.hasVillager(villager.getUUID()))
            .findFirst()
            .ifPresent(village -> adjust(village, -LOSS_VILLAGER_DEATH, "villager death"));
    }

    // -------------------------------------------------------------------------
    // Event hook — player interacts with stockpile chest (donation detection)
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public static void onPlayerInteractWithBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        var blockPos = event.getPos();
        var blockState = level.getBlockState(blockPos);
        if (!blockState.is(net.minecraft.world.level.block.Blocks.CHEST)) return;

        VillageRegistry.get(level).all().stream()
            .filter(v -> v.getChestTracker().positions().contains(blockPos))
            .findFirst()
            .ifPresent(village -> {
                ItemStack carried = event.getEntity().getMainHandItem();
                if (!carried.isEmpty()) {
                    adjust(village, GAIN_PLAYER_DONATION, "player chest interaction");
                }
            });
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private static void adjust(SmartVillage village, int delta, String reason) {
        int before = village.getProsperityScore();
        village.addProsperity(delta);
        // Clamp to 0 minimum.
        if (village.getProsperityScore() < 0) {
            village.addProsperity(-village.getProsperityScore());
        }
        int after = village.getProsperityScore();
        if (before != after) {
            LOGGER.debug("[SmartVillager] Village at {} prosperity {} → {} ({})",
                village.getAnchor(), before, after, reason);
        }
    }
}
