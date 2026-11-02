package com.banneditemsmod.handler;

import com.banneditemsmod.config.BannedItemsConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Enforces the banned-item rules through several complementary mechanisms:
 *
 *  1. Periodic inventory sweep  (every second / 20 ticks)
 *     Removes any banned item from every inventory slot and the cursor.
 *
 *  2. Crafting intercept
 *     Zeroes the crafted stack if its item is banned.
 *
 *  3. Right-click cancellation
 *     Prevents banned items being used on blocks or in the air.
 *
 *  4. Item pickup cancellation
 *     Prevents banned item entities being picked up from the ground.
 *
 *  5. Anvil update hook
 *     Prevents banned items from being repaired / renamed in an anvil.
 */
public class BannedItemHandler {

    /** How often (in server ticks) the inventory is swept. 20 = once per second. */
    private static final int SWEEP_INTERVAL = 20;

    // -------------------------------------------------------------------------
    // 1. Inventory sweep
    // -------------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        // Stagger checks so not every player is scanned on the same tick
        if ((player.tickCount + player.getId()) % SWEEP_INTERVAL != 0) return;

        sweepInventory(player);
    }

    private void sweepInventory(ServerPlayer player) {
        Inventory inv = player.getInventory();
        boolean removed = false;

        // Main inventory + hotbar + off-hand (slots 0-40)
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && BannedItemsConfig.isBanned(stack)) {
                inv.setItem(i, ItemStack.EMPTY);
                removed = true;
            }
        }

        // Cursor slot (item held by the mouse inside any open container)
        ItemStack carried = player.containerMenu.getCarried();
        if (!carried.isEmpty() && BannedItemsConfig.isBanned(carried)) {
            player.containerMenu.setCarried(ItemStack.EMPTY);
            removed = true;
        }

        if (removed) {
            player.sendSystemMessage(Component.literal(
                "§c[BannedItems] §7Banned item(s) were removed from your inventory."));
            player.inventoryMenu.broadcastChanges();
        }
    }

    // -------------------------------------------------------------------------
    // 2. Crafting intercept
    // -------------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ItemStack result = event.getCrafting();
        if (!result.isEmpty() && BannedItemsConfig.isBanned(result)) {
            // Zero out the result so the player receives nothing
            result.setCount(0);
            player.sendSystemMessage(Component.literal(
                "§c[BannedItems] §7You cannot craft that item — it is banned!"));
        }
    }

    // -------------------------------------------------------------------------
    // 3a. Right-click on block
    // -------------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack item = event.getItemStack();

        if (!item.isEmpty() && BannedItemsConfig.isBanned(item)) {
            event.setCanceled(true);
            player.sendSystemMessage(Component.literal(
                "§c[BannedItems] §7That item is banned and cannot be used."));
        }
    }

    // -------------------------------------------------------------------------
    // 3b. Right-click in air
    // -------------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack item = event.getItemStack();

        if (!item.isEmpty() && BannedItemsConfig.isBanned(item)) {
            event.setCanceled(true);
            player.sendSystemMessage(Component.literal(
                "§c[BannedItems] §7That item is banned and cannot be used."));
        }
    }

    // -------------------------------------------------------------------------
    // 3c. Left-click on entity (attacking with banned item)
    // -------------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onAttackEntity(net.neoforged.neoforge.event.entity.player.AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack item = player.getMainHandItem();

        if (!item.isEmpty() && BannedItemsConfig.isBanned(item)) {
            event.setCanceled(true);
            // Don't spam the message on every attack tick; sweep will handle it soon
        }
    }

    // -------------------------------------------------------------------------
    // 4. Item pickup from ground
    // -------------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onItemPickup(ItemEntityPickupEvent.Pre event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;

        ItemStack item = event.getItemEntity().getItem();
        if (!item.isEmpty() && BannedItemsConfig.isBanned(item)) {
            event.setCanPickup(net.neoforged.neoforge.common.util.TriState.FALSE);
            // Destroy the item entity on the ground so it cannot be re-picked up
            event.getItemEntity().discard();
            player.sendSystemMessage(Component.literal(
                "§c[BannedItems] §7A banned item on the ground was destroyed: §e"
                + item.getHoverName().getString()));
        }
    }

    // -------------------------------------------------------------------------
    // 5. Anvil – block repair / rename of banned items
    // -------------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left  = event.getLeft();
        ItemStack right = event.getRight();

        if ((!left.isEmpty()  && BannedItemsConfig.isBanned(left)) ||
            (!right.isEmpty() && BannedItemsConfig.isBanned(right))) {
            // Returning without setting an output cancels the recipe
            event.setOutput(ItemStack.EMPTY);
            event.setCost(0);
        }
    }
}
