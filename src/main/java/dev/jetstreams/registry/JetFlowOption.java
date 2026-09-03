package dev.jetstreams.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Option payload for the directional stream particle: the cardinal direction the
 * current flows toward, so client FX can color and align the visuals with the
 * same palette used for the screen tints.
 */
public record JetFlowOption(int direction) implements ParticleOptions {
    /** -Z (north). */
    public static final int NORTH = 0;
    /** +Z (south). */
    public static final int SOUTH = 1;
    /** +X (east). */
    public static final int EAST = 2;
    /** -X (west). */
    public static final int WEST = 3;

    public static final Codec<JetFlowOption> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    Codec.INT.fieldOf("direction").forGetter(JetFlowOption::direction)
            ).apply(instance, JetFlowOption::new));
    public static final MapCodec<JetFlowOption> MAP_CODEC = CODEC.fieldOf("jetstreams:flow");

    public static final StreamCodec<net.minecraft.network.FriendlyByteBuf, JetFlowOption> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, JetFlowOption::direction, JetFlowOption::new);

    @Override
    public ParticleType<?> getType() {
        return ModParticles.JET_STREAK;
    }
}
