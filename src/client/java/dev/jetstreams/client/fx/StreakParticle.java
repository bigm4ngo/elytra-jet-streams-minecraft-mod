package dev.jetstreams.client.fx;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

/**
 * A small translucent streak that drifts along the stream flow. Spawned in short chains
 * along the wind direction, chains read as elongated motion lines. Fades in fast, out slow.
 */
public class StreakParticle extends SingleQuadParticle {
    private final float baseAlpha;

    protected StreakParticle(ClientLevel level, double x, double y, double z,
                             double vx, double vy, double vz, SpriteSet sprites,
                             int lifetime, float size, float alpha) {
        super(level, x, y, z, vx, vy, vz, sprites.first());
        this.lifetime = lifetime;
        this.quadSize = size;
        this.hasPhysics = false;
        this.gravity = 0.0F;
        this.baseAlpha = alpha;
        this.setAlpha(0.0F);
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

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double vx, double vy, double vz, RandomSource random) {
            int lifetime = 20 + random.nextInt(18);
            float size = 0.12F + random.nextFloat() * 0.16F;
            float alpha = 0.10F + random.nextFloat() * 0.08F;
            return new StreakParticle(level, x, y, z, vx, vy, vz, this.sprites, lifetime, size, alpha);
        }
    }
}
