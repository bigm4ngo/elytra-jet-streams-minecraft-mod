package dev.jetstreams.registry;

import com.mojang.serialization.MapCodec;
import dev.jetstreams.JetStreams;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;

/** Client-spawned FX particle types. Registered on both sides (registry requirement). */
public final class ModParticles {
    /**
     * Directional stream streak: carries a {@link JetFlowOption} with the cardinal flow
     * direction so clients can color it with the same palette as the screen tints.
     */
    public static final ParticleType<JetFlowOption> JET_STREAK = register("jet_streak",
            JetFlowOption.MAP_CODEC, JetFlowOption.STREAM_CODEC);
    /** Large, faint, flow-aligned cirrus band puff near cruise altitude. */
    public static final SimpleParticleType CIRRUS_BAND = register("cirrus_band");
    /** Bright rim marker: big, high-contrast particle that lines tunnel walls. */
    public static final SimpleParticleType RIM_MARKER = register("rim_marker");

    private ModParticles() {}

    private static SimpleParticleType register(String name) {
        return Registry.register(BuiltInRegistries.PARTICLE_TYPE, JetStreams.id(name),
                FabricParticleTypes.simple(false));
    }

    private static <T extends net.minecraft.core.particles.ParticleOptions> ParticleType<T> register(
            String name, MapCodec<T> codec, net.minecraft.network.codec.StreamCodec<
                    ? super net.minecraft.network.RegistryFriendlyByteBuf, T> streamCodec) {
        return Registry.register(BuiltInRegistries.PARTICLE_TYPE, JetStreams.id(name),
                FabricParticleTypes.complex(false, codec, streamCodec));
    }

    /** Force classloading; particle constants register in static init. */
    public static void init() {}
}
