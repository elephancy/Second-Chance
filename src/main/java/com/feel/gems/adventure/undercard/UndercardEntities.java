package com.feel.gems.adventure.undercard;

import com.feel.gems.GemsMod;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** Registered separately from player-spawnable practice targets. */
public final class UndercardEntities {
    private static final Identifier CONSTRUCT_ID = Identifier.fromNamespaceAndPath(
            GemsMod.MOD_ID, "undercard_construct");
    public static final EntityType<UndercardConstructEntity> CONSTRUCT = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, CONSTRUCT_ID,
            EntityType.Builder.of(UndercardConstructEntity::new, MobCategory.MISC)
                    .sized(0.8F, 2.1F).eyeHeight(1.8F)
                    .clientTrackingRange(64).updateInterval(1)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, CONSTRUCT_ID)));

    private UndercardEntities() { }

    public static void init() {
        FabricDefaultAttributeRegistry.register(CONSTRUCT,
                UndercardConstructEntity.createAttributes());
    }
}
