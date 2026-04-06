package com.feel.gems.client.adventure.boss;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SimpleAnimatedParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

/** A stationary, short-lived stage cue; old acts must not linger into the next strike. */
public final class CadenzaMarkParticle extends SimpleAnimatedParticle {
    private CadenzaMarkParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z, sprites, 0.0F);
        this.lifetime = 8;
        this.quadSize = 0.38F;
        this.xd = this.yd = this.zd = 0.0;
        setSpriteFromAge(sprites);
    }

    @Override
    public int getLightCoords(float partialTick) {
        return 0xF000F0;
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed,
                                       RandomSource random) {
            return new CadenzaMarkParticle(level, x, y, z, sprites);
        }
    }
}
