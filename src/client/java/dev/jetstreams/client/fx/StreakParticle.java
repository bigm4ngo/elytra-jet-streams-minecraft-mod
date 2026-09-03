package dev.jetstreams.client.fx;

import dev.jetstreams.client.TintPalette;
import dev.jetstreams.registry.JetFlowOption;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.RandomSource;

/**
 * A small translucent streak that drifts along the stream flow. Spawned in short chains
 * along the wind direction, chains read as elongated motion lines. Fades in fast, out slow.
 * Tinted with the configured color for the current's cardinal direction.
 */
public class StreakParticle extends SingleQuadParticle {
    private final float baseAlpha;

    protected StreakParticle(ClientLevel level, double x, double y, double z,
                             double vx, double vy, double vz, SpriteSet sprites,
                             int lifetime, float size, float alpha, int color) {
        super(level, x, y, z, vx, vy, vz, sprites.first());
        this.lifetime = lifetime;
        this.quadSize = size;
        this.hasPhysics = false;
        this.gravity = 0.0F;
        this.baseAlpha = alpha;
        this.setAlpha(0.0F);
        this.setColor(
                ((color >> 16) & 0xFF) / 255.0F,
                ((color >> 8) & 0xFF) / 255.0F,
                (color & 0xFF) / 255.0F);
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }
        this.x += this.xd;
        this.y += this.yd;
        this.z += this.zd;
        float fadeIn = Math.min(1.0F, this.age / 5.0F);
        float fadeOut = Math.min(1.0F, (this.lifetime - this.age) / (this.lifetime * 0.5F));
        this.setAlpha(this.baseAlpha * fadeIn * fadeOut);
    }

    @Override
    public SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }

    public static class Provider implements ParticleProvider<JetFlowOption> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(JetFlowOption type, ClientLevel level,
                                       double x, double y, double z,
                                       double vx, double vy, double vz, RandomSource random) {
            int lifetime = 24 + random.nextInt(20);
            float size = 0.26F + random.nextFloat() * 0.20F;
            float alpha = 0.30F + random.nextFloat() * 0.14F;
            int color = TintPalette.forDirection(type.direction());
            return new StreakParticle(level, x, y, z, vx, vy, vz, this.sprites,
                    lifetime, size, alpha, color);
        }
    }

    /**
     * Bigger, brighter sibling used for tunnel <b>rim markers</b> - the wall lining that
     * makes stream borders unmistakable. Spawned white for maximum contrast against the
     * tinted flow streaks: the rim reads "wall", the color inside reads "which current".
     */
    public static class RimProvider implements ParticleProvider<net.minecraft.core.particles.SimpleParticleType> {
        private final SpriteSet sprites;

        public RimProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(net.minecraft.core.particles.SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double vx, double vy, double vz, RandomSource random) {
            int lifetime = 18 + random.nextInt(14);
            float size = 0.40F + random.nextFloat() * 0.30F;
            float alpha = 0.55F + random.nextFloat() * 0.25F;
            return new StreakParticle(level, x, y, z, vx, vy, vz, this.sprites,
                    lifetime, size, alpha, 0xFFFFFF);
        }
    }
}
