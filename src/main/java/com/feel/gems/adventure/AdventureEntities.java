package com.feel.gems.adventure;

import com.feel.gems.GemsMod;
import com.feel.gems.entity.DomainCreatureEntity;
import com.feel.gems.entity.StorybookFoldwingEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** Campaign-only entities retain their original gems namespace for saved worlds. */
public final class AdventureEntities {
    private static final Identifier FOLDWING_ID = Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, "storybook_foldwing");

    public static final EntityType<StorybookFoldwingEntity> STORYBOOK_FOLDWING = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, FOLDWING_ID,
            EntityType.Builder.of(StorybookFoldwingEntity::new, MobCategory.MISC)
                    .sized(0.7F, 0.6F).clientTrackingRange(48).updateInterval(3)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, FOLDWING_ID)));

    public static final EntityType<DomainCreatureEntity> INKLING = creature(DomainCreatureEntity.Kind.INKLING);
    public static final EntityType<DomainCreatureEntity> REEDRUNNER = creature(DomainCreatureEntity.Kind.REEDRUNNER);
    public static final EntityType<DomainCreatureEntity> BEACON_GULL = creature(DomainCreatureEntity.Kind.BEACON_GULL);
    public static final EntityType<DomainCreatureEntity> CHORUS_HARE = creature(DomainCreatureEntity.Kind.CHORUS_HARE);
    public static final EntityType<DomainCreatureEntity> CAIRN_BEETLE = creature(DomainCreatureEntity.Kind.CAIRN_BEETLE);
    public static final EntityType<DomainCreatureEntity> CATALOG_OWL = creature(DomainCreatureEntity.Kind.CATALOG_OWL);
    public static final EntityType<DomainCreatureEntity> DUST_SKIPPER = creature(DomainCreatureEntity.Kind.DUST_SKIPPER);
    public static final EntityType<DomainCreatureEntity> RIVET_CRAB = creature(DomainCreatureEntity.Kind.RIVET_CRAB);
    public static final EntityType<DomainCreatureEntity> SPLITSTONE_RAM = creature(DomainCreatureEntity.Kind.SPLITSTONE_RAM);

    private static EntityType<DomainCreatureEntity> creature(DomainCreatureEntity.Kind kind) {
        Identifier id = Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, kind.id);
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, id,
                EntityType.Builder.<DomainCreatureEntity>of(
                                (type, level) -> new DomainCreatureEntity(type, level, kind), MobCategory.CREATURE)
                        .sized(0.8F, 0.75F).clientTrackingRange(48).updateInterval(3)
                        .build(ResourceKey.create(Registries.ENTITY_TYPE, id)));
    }

    private AdventureEntities() {
    }

    public static void init() {
        FabricDefaultAttributeRegistry.register(STORYBOOK_FOLDWING, StorybookFoldwingEntity.createAttributes());
        for (EntityType<DomainCreatureEntity> creature : java.util.List.of(INKLING,
                REEDRUNNER, BEACON_GULL, CHORUS_HARE, CAIRN_BEETLE, CATALOG_OWL,
                DUST_SKIPPER, RIVET_CRAB, SPLITSTONE_RAM)) {
            FabricDefaultAttributeRegistry.register(creature, DomainCreatureEntity.createAttributes());
        }
    }
}
