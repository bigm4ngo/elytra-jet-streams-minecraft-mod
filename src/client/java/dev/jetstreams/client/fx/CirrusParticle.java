package dev.jetstreams.client.fx;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

/**
 * A very large, very faint billboard drifting with the current - the mod's stand-in for
 * cirrus bands inside the streams. Sparsely spawned on a deterministic lattice aligned
 * with the flow field, so what the player sees always matches the actual stream layout.
 */
public class CirrusParticle extends SingleQuadParticle {
    private final float baseAlpha;

    protected CirrusParticle(ClientLevel level, double x, double y, double z,
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
        // Very slow breathing fade so bands dissolve rather than pop.
        float life = this.lifetime;
        float fadeIn = Math.min(1.0F, this.age / (life * 0.25F));
        float fadeOut = Math.min(1.0F, (life - this.age) / (life * 0.35F));
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
            int lifetime = 500 + random.nextInt(400);
            float size = 24.0F + random.nextFloat() * 22.0F;
            float alpha = 0.10F + random.nextFloat() * 0.07F;
            return new CirrusParticle(level, x, y, z, vx, vy, vz, this.sprites, lifetime, size, alpha);
        }
    }
}
