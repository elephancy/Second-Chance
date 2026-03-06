package com.feel.gems.adventure;

import com.feel.gems.GemsMod;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/** Campaign particles keep their established gems identifiers. */
public final class AdventureParticles {
    public static final SimpleParticleType CADENZA_SPOTLIGHT = register("cadenza_spotlight");
    public static final SimpleParticleType CADENZA_CROSSCUT = register("cadenza_crosscut");
    public static final SimpleParticleType CADENZA_CHORUS = register("cadenza_chorus");
    public static final SimpleParticleType CADENZA_LOCK = register("cadenza_lock");

    private AdventureParticles() {
    }

    public static void init() {
        // Triggers static registration.
    }

    private static SimpleParticleType register(String path) {
        return Registry.register(BuiltInRegistries.PARTICLE_TYPE,
                Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, path), FabricParticleTypes.simple());
    }
}
