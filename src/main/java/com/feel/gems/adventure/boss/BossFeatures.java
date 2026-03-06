package com.feel.gems.adventure.boss;

import com.feel.gems.GemsMod;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;

/** Registration hook: call once in GemsMod.onInitialize after AdventureItems.init(). */
public final class BossFeatures {
    private static final Identifier VERDICT_ID = Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, "painted_verdict");
    private static final Identifier SKETCHGUARD_ID = Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, "sketchguard");
    private static final Identifier LAST_ARRIVAL_ID = Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, "last_arrival");
    private static final Identifier PUNCHLINE_ID = Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, "unbroken_punchline");
    private static final Identifier SOLVER_ID = Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, "second_move_solver");
    private static final Identifier REDLINE_ID = Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, "red_line_between");
    private static final Identifier ENCORE_ID = Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, "empty_encore");
    private static final Identifier ECHO_ID = Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, "signal_echo");
    private static final Identifier CADENZA_ID = Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, "stray_cadenza");
    private static final Identifier STAGE_ID = Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, "stage_mark");
    private static final Identifier FRAME_ID = Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, "parallax_frame");
    private static final Identifier TICKET_ID = Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, "return_ticket");
    private static final Identifier MARK_ID = Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, "rehearsal_mark");
    private static final Identifier LENS_ID = Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, "route_lens");
    private static final Identifier RESCUE_ID = Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, "rescue_line");
    private static final Identifier DIAL_ID = Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, "signal_dial");
    public static final EntityType<PaintedVerdictEntity> PAINTED_VERDICT = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, VERDICT_ID,
            EntityType.Builder.of(PaintedVerdictEntity::new, MobCategory.MONSTER)
                    .sized(0.9F, 2.1F).eyeHeight(1.8F)
                    .clientTrackingRange(80).updateInterval(2)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, VERDICT_ID)));
    public static final EntityType<SketchguardEntity> SKETCHGUARD = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, SKETCHGUARD_ID,
            EntityType.Builder.of(SketchguardEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.8F).eyeHeight(1.5F)
                    .clientTrackingRange(64).updateInterval(3)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, SKETCHGUARD_ID)));
    public static final EntityType<LastArrivalEntity> LAST_ARRIVAL = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, LAST_ARRIVAL_ID,
            EntityType.Builder.of(LastArrivalEntity::new, MobCategory.MONSTER)
                    .sized(1.0F, 2.2F).eyeHeight(1.9F)
                    .clientTrackingRange(80).updateInterval(2)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, LAST_ARRIVAL_ID)));
    public static final EntityType<UnbrokenPunchlineEntity> UNBROKEN_PUNCHLINE = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, PUNCHLINE_ID,
            EntityType.Builder.of(UnbrokenPunchlineEntity::new, MobCategory.MONSTER)
                    .sized(0.9F, 2.1F).eyeHeight(1.8F)
                    .clientTrackingRange(80).updateInterval(2)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, PUNCHLINE_ID)));
    public static final EntityType<SecondMoveSolverEntity> SECOND_MOVE_SOLVER = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, SOLVER_ID,
            EntityType.Builder.of(SecondMoveSolverEntity::new, MobCategory.MONSTER)
                    .sized(0.9F, 2.2F).eyeHeight(1.9F)
                    .clientTrackingRange(80).updateInterval(2)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, SOLVER_ID)));
    public static final EntityType<RedLineBetweenEntity> RED_LINE_BETWEEN = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, REDLINE_ID,
            EntityType.Builder.of(RedLineBetweenEntity::new, MobCategory.MONSTER)
                    .sized(1.0F, 2.2F).eyeHeight(1.9F)
                    .clientTrackingRange(80).updateInterval(2)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, REDLINE_ID)));
    public static final EntityType<EmptyEncoreEntity> EMPTY_ENCORE = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, ENCORE_ID,
            EntityType.Builder.of(EmptyEncoreEntity::new, MobCategory.MONSTER)
                    .sized(1.0F, 2.2F).eyeHeight(1.9F)
                    .clientTrackingRange(80).updateInterval(2)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, ENCORE_ID)));
    public static final EntityType<SignalEchoEntity> SIGNAL_ECHO = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, ECHO_ID,
            EntityType.Builder.of(SignalEchoEntity::new, MobCategory.CREATURE)
                    .sized(0.65F, 1.8F).eyeHeight(1.5F)
                    .clientTrackingRange(64).updateInterval(2)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, ECHO_ID)));
    public static final EntityType<StrayCadenzaEntity> STRAY_CADENZA = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, CADENZA_ID,
            EntityType.Builder.of(StrayCadenzaEntity::new, MobCategory.MONSTER)
                    .sized(1.0F, 2.2F).eyeHeight(1.9F)
                    .clientTrackingRange(80).updateInterval(2)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, CADENZA_ID)));
    public static final EntityType<RegionalSentryEntity> SIGNAL_WARDEN =
            registerSentry("signal_warden", RegionalSentryEntity.Region.LASTLIGHT);
    public static final EntityType<RegionalSentryEntity> UNDERSTUDY =
            registerSentry("understudy", RegionalSentryEntity.Region.REHEARSAL);
    public static final EntityType<RegionalSentryEntity> MARGIN_SHADE =
            registerSentry("margin_shade", RegionalSentryEntity.Region.BRANCH);
    public static final EntityType<RegionalSentryEntity> WATCHKEEPER =
            registerSentry("watchkeeper", RegionalSentryEntity.Region.REDLINE);
    public static final EntityType<RegionalSentryEntity> RELAY_PHANTOM =
            registerSentry("relay_phantom", RegionalSentryEntity.Region.HUSHWIRE);
    public static final EntityType<RegionalSentryEntity> UNCAST =
            registerSentry("uncast", RegionalSentryEntity.Region.UNWRITTEN);
    public static final Item PARALLAX_FRAME = Registry.register(BuiltInRegistries.ITEM, FRAME_ID,
            new ParallaxFrameItem(new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, FRAME_ID)).stacksTo(1)));

    public static final Item RETURN_TICKET = Registry.register(BuiltInRegistries.ITEM, TICKET_ID,
            new ReturnTicketItem(new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, TICKET_ID)).stacksTo(1)));

    public static final Item REHEARSAL_MARK = Registry.register(BuiltInRegistries.ITEM, MARK_ID,
            new RehearsalMarkItem(new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, MARK_ID)).stacksTo(1)));

    public static final Item ROUTE_LENS = Registry.register(BuiltInRegistries.ITEM, LENS_ID,
            new RouteLensItem(new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, LENS_ID)).stacksTo(1)));

    public static final Item RESCUE_LINE = Registry.register(BuiltInRegistries.ITEM, RESCUE_ID,
            new RescueLineItem(new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, RESCUE_ID)).stacksTo(1)));

    public static final Item SIGNAL_DIAL = Registry.register(BuiltInRegistries.ITEM, DIAL_ID,
            new SignalDialItem(new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, DIAL_ID)).stacksTo(1)));

    public static final Item STAGE_MARK = Registry.register(BuiltInRegistries.ITEM, STAGE_ID,
            new StageMarkItem(new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, STAGE_ID)).stacksTo(1)));

    private BossFeatures() { }

    private static EntityType<RegionalSentryEntity> registerSentry(
            String path, RegionalSentryEntity.Region region) {
        Identifier id = Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, path);
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, id,
                EntityType.Builder.<RegionalSentryEntity>of(
                        (type, world) -> new RegionalSentryEntity(type, world, region),
                        MobCategory.MONSTER)
                        .sized(0.75F, 1.9F).eyeHeight(1.6F)
                        .clientTrackingRange(64).updateInterval(3)
                        .build(ResourceKey.create(Registries.ENTITY_TYPE, id)));
    }

    public static void init() {
        FabricDefaultAttributeRegistry.register(PAINTED_VERDICT, PaintedVerdictEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(SKETCHGUARD, SketchguardEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(LAST_ARRIVAL, LastArrivalEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(UNBROKEN_PUNCHLINE, UnbrokenPunchlineEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(SECOND_MOVE_SOLVER, SecondMoveSolverEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(RED_LINE_BETWEEN, RedLineBetweenEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(EMPTY_ENCORE, EmptyEncoreEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(SIGNAL_ECHO, SignalEchoEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(STRAY_CADENZA, StrayCadenzaEntity.createAttributes());
        for (EntityType<RegionalSentryEntity> type : List.of(
                SIGNAL_WARDEN, UNDERSTUDY, MARGIN_SHADE,
                WATCHKEEPER, RELAY_PHANTOM, UNCAST)) {
            FabricDefaultAttributeRegistry.register(type, RegionalSentryEntity.createAttributes());
        }
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                CampaignSignatures.deliver(handler.getPlayer()));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 100 != 0) return;
            for (var player : server.getPlayerList().getPlayers()) {
                CampaignSignatures.deliverQuietly(player);
            }
        });
    }
}
