package dev.jetstreams.client;

import dev.jetstreams.JetStreams;
import dev.jetstreams.client.fx.CirrusParticle;
import dev.jetstreams.client.fx.StreakParticle;
import dev.jetstreams.client.fx.StreamParticleSpawner;
import dev.jetstreams.client.hud.StreamOverlayHud;
import dev.jetstreams.client.mixinhooks.SkyTint;
import dev.jetstreams.registry.ModParticles;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;

/** Client entry point: FX registration, HUD, network receiver. No gameplay authority here. */
public class JetStreamsClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientNetworking.register();
        SkyTint.init(); // no-op guard so the class is clearly owned by the client entrypoint

        ParticleProviderRegistry.getInstance().register(ModParticles.JET_STREAK, StreakParticle.Provider::new);
        ParticleProviderRegistry.getInstance().register(ModParticles.CIRRUS_BAND, CirrusParticle.Provider::new);

        ClientTickEvents.END_CLIENT_TICK.register(StreamParticleSpawner::tick);
        StreamOverlayHud.register();

        JetStreams.LOGGER.info("Elytra Jet Streams client initialized");
    }
}
