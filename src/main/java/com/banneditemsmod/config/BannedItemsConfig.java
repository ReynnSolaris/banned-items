package com.banneditemsmod.config;

import com.banneditemsmod.BannedItemsMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLPaths;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages the persisted list of banned items.
 *
 * On disk  : config/banneditems.json  –  a JSON array of SNBT strings.
 * In memory: a parallel list of parsed ItemStack objects (count normalised to 1)
 *            that is rebuilt every time the server starts.
 *
 * Matching uses ItemStack.isSameItemSameComponents() so both the item type AND
 * all data-components (enchantments, custom name, etc.) must match exactly.
 */
public class BannedItemsConfig {

    private static final Path CONFIG_PATH =
            FMLPaths.CONFIGDIR.get().resolve("banneditems.json");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Raw SNBT strings persisted to disk. */
    private static final List<String> bannedSnbtList = new ArrayList<>();

    /** Parsed ItemStacks built from the SNBT list – needs registry access to populate. */
    private static final List<ItemStack> bannedItemStacks = new CopyOnWriteArrayList<>();

    // -------------------------------------------------------------------------
    // Disk I/O
    // -------------------------------------------------------------------------

    /** Reads banneditems.json from the config directory; creates an empty file if absent. */
    public static void load() {
        if (!Files.exists(CONFIG_PATH)) {
            save(); // create empty file
            return;
        }
        try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
            JsonArray array = GSON.fromJson(reader, JsonArray.class);
            bannedSnbtList.clear();
            if (array != null) {
                for (JsonElement element : array) {
                    bannedSnbtList.add(element.getAsString());
                }
            }
            BannedItemsMod.LOGGER.info("BannedItemsConfig: read {} SNBT entries from disk.", bannedSnbtList.size());
        } catch (Exception e) {
            BannedItemsMod.LOGGER.error("BannedItemsConfig: failed to load config.", e);
        }
    }

    /** Writes the current SNBT list back to banneditems.json. */
    public static void save() {
        JsonArray array = new JsonArray();
        for (String snbt : bannedSnbtList) {
            array.add(snbt);
        }
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(array, writer);
            }
        } catch (Exception e) {
            BannedItemsMod.LOGGER.error("BannedItemsConfig: failed to save config.", e);
        }
    }

    // -------------------------------------------------------------------------
    // Registry-dependent initialisation (called from ServerStartedEvent)
    // -------------------------------------------------------------------------

    /**
     * Parses every stored SNBT string into a live ItemStack using the server's
     * registry access.  Must be called after the server has started.
     */
    public static void loadItemStacks(HolderLookup.Provider registries) {
        bannedItemStacks.clear();
        for (String snbt : bannedSnbtList) {
            try {
                CompoundTag tag = TagParser.parseTag(snbt);
                ItemStack.parse(registries, tag).ifPresent(stack -> {
                    stack.setCount(1); // normalise – count is irrelevant for banning
                    bannedItemStacks.add(stack);
                });
            } catch (Exception e) {
                BannedItemsMod.LOGGER.error("BannedItemsConfig: could not parse SNBT → '{}': {}", snbt, e.getMessage());
            }
        }
        BannedItemsMod.LOGGER.info("BannedItemsConfig: {} banned ItemStack(s) active.", bannedItemStacks.size());
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Returns true if the given stack matches any banned entry.
     * Comparison ignores count but respects all data-components (NBT equivalent).
     */
    public static boolean isBanned(ItemStack stack) {
        if (stack.isEmpty()) return false;
        for (ItemStack banned : bannedItemStacks) {
            if (ItemStack.isSameItemSameComponents(stack, banned)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Serialises the given stack (count normalised to 1) and appends it to the
     * ban list, then saves to disk.  Does nothing if the item is already banned.
     *
     * @return true if the item was newly added, false if it was already present.
     */
    public static boolean addBannedItem(ItemStack stack, HolderLookup.Provider registries) {
        if (isBanned(stack)) return false;

        ItemStack normalised = stack.copyWithCount(1);

        // Serialise to SNBT via the Tag API introduced in 1.20.5+
        String snbt = normalised.save(registries).toString();

        bannedSnbtList.add(snbt);
        bannedItemStacks.add(normalised);
        save();
        return true;
    }

    /**
     * Removes the first banned entry that matches the given stack.
     *
     * @return true if an entry was removed.
     */
    public static boolean removeBannedItem(ItemStack stack) {
        for (int i = 0; i < bannedItemStacks.size(); i++) {
            if (ItemStack.isSameItemSameComponents(stack, bannedItemStacks.get(i))) {
                bannedItemStacks.remove(i);
                bannedSnbtList.remove(i);
                save();
                return true;
            }
        }
        return false;
    }

    /** Read-only view of the current in-memory banned stacks. */
    public static List<ItemStack> getBannedItemStacks() {
        return Collections.unmodifiableList(bannedItemStacks);
    }

    /** Total number of banned entries (same as what is stored on disk). */
    public static int getBannedCount() {
        return bannedSnbtList.size();
    }
}
