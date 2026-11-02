package com.banneditemsmod;

import com.banneditemsmod.command.BanHandItemCommand;
import com.banneditemsmod.config.BannedItemsConfig;
import com.banneditemsmod.handler.BannedItemHandler;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(BannedItemsMod.MODID)
public class BannedItemsMod {

    public static final String MODID = "banneditemsmod";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    public BannedItemsMod(IEventBus modEventBus) {
        // Load the config from disk immediately at startup
        BannedItemsConfig.load();

        // Register our forge event listeners
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.register(new BannedItemHandler());

        LOGGER.info("BannedItemsMod initialized.");
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        BanHandItemCommand.register(event.getDispatcher(), event.getBuildContext());
    }

    private void onServerStarted(ServerStartedEvent event) {
        // Parse SNBT strings into live ItemStack objects once we have registry access
        BannedItemsConfig.loadItemStacks(event.getServer().registryAccess());
        LOGGER.info("BannedItemsMod: loaded {} banned item(s) from config.", BannedItemsConfig.getBannedCount());
    }
}
