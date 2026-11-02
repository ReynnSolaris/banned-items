package com.banneditemsmod.command;

import com.banneditemsmod.BannedItemsMod;
import com.banneditemsmod.config.BannedItemsConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Registers three operator-only commands:
 *
 *   /banhanditem        – bans the item currently in the player's main hand
 *                         (removes it immediately from their hand, then from any
 *                          inventory slot that holds an identical copy).
 *
 *   /unbanhanditem      – unbans the item currently in the player's main hand.
 *
 *   /banneditemslist    – prints all currently banned items.
 *
 * All commands require permission level 2 (operator).
 */
public class BanHandItemCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                CommandBuildContext buildContext) {

        dispatcher.register(
            Commands.literal("banhanditem")
                .requires(src -> src.hasPermission(2))
                .executes(BanHandItemCommand::executeBan)
        );

        dispatcher.register(
            Commands.literal("unbanhanditem")
                .requires(src -> src.hasPermission(2))
                .executes(BanHandItemCommand::executeUnban)
        );

        dispatcher.register(
            Commands.literal("banneditemslist")
                .requires(src -> src.hasPermission(2))
                .executes(BanHandItemCommand::executeList)
        );

        dispatcher.register(
            Commands.literal("reloadbanneditems")
                .requires(src -> src.hasPermission(2))
                .executes(BanHandItemCommand::executeReload)
        );
    }

    // -------------------------------------------------------------------------
    // /banhanditem
    // -------------------------------------------------------------------------

    private static int executeBan(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();

        ServerPlayer player = getPlayer(source);
        if (player == null) {
            source.sendFailure(Component.literal("This command must be run by a player."));
            return 0;
        }

        ItemStack held = player.getMainHandItem();

        if (held.isEmpty()) {
            source.sendFailure(Component.literal("You are not holding any item!"));
            return 0;
        }

        if (BannedItemsConfig.isBanned(held)) {
            source.sendFailure(Component.literal(
                "§e" + held.getHoverName().getString() + " §cis already banned."));
            return 0;
        }

        // Add to ban list (serialises the item including all data-components)
        boolean added = BannedItemsConfig.addBannedItem(held, player.level().registryAccess());

        if (!added) {
            source.sendFailure(Component.literal("Failed to add the item to the ban list."));
            return 0;
        }

        // Remove the item from the player's hand immediately
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

        // Sweep the rest of the inventory for copies of the same item
        int removed = removeAllMatchingFromInventory(player, held);

        String itemName = held.getHoverName().getString();
        int total = BannedItemsConfig.getBannedCount();

        source.sendSuccess(() -> Component.literal(
            "§a✔ Banned §e" + itemName +
            " §a| Removed §e" + (removed + 1) + " §ainstance(s)" +
            " | Total banned: §e" + total
        ), true /* broadcast to ops */);

        BannedItemsMod.LOGGER.info("[BannedItems] {} banned '{}'. Total: {}",
            player.getName().getString(), itemName, total);

        return 1;
    }

    // -------------------------------------------------------------------------
    // /unbanhanditem
    // -------------------------------------------------------------------------

    private static int executeUnban(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();

        ServerPlayer player = getPlayer(source);
        if (player == null) {
            source.sendFailure(Component.literal("This command must be run by a player."));
            return 0;
        }

        ItemStack held = player.getMainHandItem();

        if (held.isEmpty()) {
            source.sendFailure(Component.literal("You are not holding any item!"));
            return 0;
        }

        if (!BannedItemsConfig.isBanned(held)) {
            source.sendFailure(Component.literal(
                "§e" + held.getHoverName().getString() + " §cis not currently banned."));
            return 0;
        }

        boolean removed = BannedItemsConfig.removeBannedItem(held);

        if (removed) {
            String itemName = held.getHoverName().getString();
            source.sendSuccess(() -> Component.literal(
                "§a✔ Unbanned §e" + itemName +
                " §a| Total banned: §e" + BannedItemsConfig.getBannedCount()
            ), true);
            BannedItemsMod.LOGGER.info("[BannedItems] {} unbanned '{}'.",
                player.getName().getString(), itemName);
            return 1;
        }

        source.sendFailure(Component.literal("Failed to remove item from the ban list."));
        return 0;
    }

    // -------------------------------------------------------------------------
    // /banneditemslist
    // -------------------------------------------------------------------------

    private static int executeList(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        List<ItemStack> banned = BannedItemsConfig.getBannedItemStacks();

        if (banned.isEmpty()) {
            source.sendSuccess(() -> Component.literal("§eNo items are currently banned."), false);
            return 0;
        }

        source.sendSuccess(() -> Component.literal(
            "§6═══ Banned Items (" + banned.size() + ") ═══"), false);

        for (int i = 0; i < banned.size(); i++) {
            ItemStack stack = banned.get(i);
            final String line = "§7" + (i + 1) + ". §e" + stack.getHoverName().getString()
                + " §8[" + stack.getItem().builtInRegistryHolder().key().location() + "]";
            source.sendSuccess(() -> Component.literal(line), false);
        }

        return banned.size();
    }

    // -------------------------------------------------------------------------
    // /reloadbanneditems
    // -------------------------------------------------------------------------

    private static int executeReload(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();

        BannedItemsConfig.load();

        // Re-parse with the server's registry access
        try {
            BannedItemsConfig.loadItemStacks(source.getServer().registryAccess());
            source.sendSuccess(() -> Component.literal(
                "§a✔ Reloaded banned items config. §e" + BannedItemsConfig.getBannedCount() + " §aitem(s) active."
            ), true);
        } catch (Exception e) {
            source.sendFailure(Component.literal("Failed to reload: " + e.getMessage()));
            return 0;
        }

        return 1;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static ServerPlayer getPlayer(CommandSourceStack source) {
        try {
            return source.getPlayerOrException();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Scans every slot in the player's inventory and removes stacks that match
     * the given item (same item + same components, any count).
     *
     * @return number of slots cleared (not counting the hand slot already cleared).
     */
    private static int removeAllMatchingFromInventory(ServerPlayer player, ItemStack target) {
        var inventory = player.getInventory();
        int count = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && BannedItemsConfig.isBanned(stack)) {
                inventory.setItem(i, ItemStack.EMPTY);
                count++;
            }
        }
        if (count > 0) {
            player.inventoryMenu.broadcastChanges();
        }
        return count;
    }
}
