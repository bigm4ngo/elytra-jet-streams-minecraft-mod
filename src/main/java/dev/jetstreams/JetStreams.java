package dev.jetstreams;

import dev.jetstreams.chunk.FlightChunkPreloader;
import dev.jetstreams.command.JetStreamsCommand;
import dev.jetstreams.config.ConfigManager;
import dev.jetstreams.network.Networking;
import dev.jetstreams.registry.ModParticles;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Elytra Jet Streams — high-altitude one-way wind highways with vector-locking
 * intersections, exponential altitude speed scaling and predictive chunk streaming.
 */
public class JetStreams implements ModInitializer {
    public static final String MOD_ID = "jetstreams";
    public static final Logger LOGGER = LoggerFactory.getLogger("Elytra Jet Streams");

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        ConfigManager.load();
        dev.jetstreams.field.WindField.invalidate();
        ModParticles.init();
        FlightChunkPreloader.register();
        Networking.registerCommon();
        JetStreamsCommand.register();
        LOGGER.info("Elytra Jet Streams initialized (streams activate at y={}, cruise at y={})",
                ConfigManager.get().physics.activationAltitude,
                ConfigManager.get().physics.cruiseStartAltitude);
    }
}
