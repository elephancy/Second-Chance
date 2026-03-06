package com.feel.gems.friend;

import com.feel.gems.GemsMod;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** Registration for the playable friend interaction foundation. */
public final class FriendFeatures {
    private static final Identifier FRIEND_ID = Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, "friend");

    public static final EntityType<FriendEntity> FRIEND = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            FRIEND_ID,
            EntityType.Builder.of(FriendEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.8F)
                    .eyeHeight(1.62F)
                    .clientTrackingRange(64)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, FRIEND_ID)));

    private FriendFeatures() {
    }

    public static void init() {
        FabricDefaultAttributeRegistry.register(FRIEND, FriendEntity.createAttributes());
        FriendNetworking.register();
        ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(
                Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, "friends"),
                (ResourceManagerReloadListener) FriendRegistry::reload);
    }
}
