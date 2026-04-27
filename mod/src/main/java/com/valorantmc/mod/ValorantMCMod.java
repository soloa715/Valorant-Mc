package com.valorantmc.mod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared (server + client) mod entrypoint.
 * Registers all custom payload types so Fabric networking can route them.
 */
public class ValorantMCMod implements ModInitializer {

    public static final String MOD_ID = "valorantmc";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        // C → S
        PayloadTypeRegistry.playC2S().register(HelloPayload.TYPE,     HelloPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(BuyActionPayload.TYPE, BuyActionPayload.CODEC);

        // S → C
        PayloadTypeRegistry.playS2C().register(HudPayload.TYPE,     HudPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BuyMenuPayload.TYPE, BuyMenuPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(RadarPayload.TYPE,   RadarPayload.CODEC);
    }
}
