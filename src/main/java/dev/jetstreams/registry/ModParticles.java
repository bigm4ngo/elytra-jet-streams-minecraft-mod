package dev.jetstreams.registry;

import dev.jetstreams.JetStreams;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.SimpleParticleType;

/** Client-spawned FX particle types. Registered on both sides (registry requirement). */
public final class ModParticles {
    /** Small translucent streak drifting along the stream flow direction. */
    public static final SimpleParticleType JET_STREAK = register("jet_streak");
    /** Large, faint, flow-aligned cirrus band puff near cruise altitude. */
    public static final SimpleParticleType CIRRUS_BAND = register("cirrus_band");

    private ModParticles() {}

    private static SimpleParticleType register(String name) {
        return Registry.register(BuiltInRegistries.PARTICLE_TYPE, JetStreams.id(name),
                FabricParticleTypes.simple(false));
    }

    /** Force classloading; particle constants register in static init. */
    public static void init() {}
}
