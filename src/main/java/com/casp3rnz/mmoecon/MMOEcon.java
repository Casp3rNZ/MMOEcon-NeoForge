package com.casp3rnz.mmoecon;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(MMOEcon.MOD_ID)
public class MMOEcon {
    public static final String MOD_ID = "mmoecon";
    public static final Logger LOGGER = LoggerFactory.getLogger(MMOEcon.MOD_ID);

    public MMOEcon(IEventBus modEventBus, ModContainer modContainer) {

        // Register TOML Config
        modContainer.registerConfig(ModConfig.Type.SERVER, Config.SPEC);

        // Register NeoForge game event listeners
        NeoForge.EVENT_BUS.register(EconomyCommands.class);
        NeoForge.EVENT_BUS.register(PlayerBalanceManager.class);
        NeoForge.EVENT_BUS.register(SellWandListener.class);
        NeoForge.EVENT_BUS.register(MMOEcon.class);
    }

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        if (Config.ENABLE_GUI_SHOP.get()) {
            ShopItemManager.load();
        }
        LOGGER.info("MMOEcon server starting — config loaded.");
    }

}
