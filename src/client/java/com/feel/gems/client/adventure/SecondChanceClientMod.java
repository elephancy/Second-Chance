package com.feel.gems.client.adventure;

import com.feel.gems.adventure.AdventureEntities;
import com.feel.gems.adventure.AdventureParticles;
import com.feel.gems.adventure.boss.BossFeatures;
import com.feel.gems.adventure.undercard.UndercardEntities;
import com.feel.gems.client.adventure.boss.CadenzaMarkParticle;
import com.feel.gems.client.adventure.boss.EmptyEncoreRenderer;
import com.feel.gems.client.adventure.boss.LastArrivalRenderer;
import com.feel.gems.client.adventure.boss.PaintedVerdictRenderer;
import com.feel.gems.client.adventure.boss.RedLineBetweenRenderer;
import com.feel.gems.client.adventure.boss.RegionalSentryRenderer;
import com.feel.gems.client.adventure.boss.SecondMoveSolverRenderer;
import com.feel.gems.client.adventure.boss.SignalEchoRenderer;
import com.feel.gems.client.adventure.boss.SketchguardRenderer;
import com.feel.gems.client.adventure.boss.StrayCadenzaRenderer;
import com.feel.gems.client.adventure.boss.UnbrokenPunchlineRenderer;
import com.feel.gems.client.casino.CasinoClientNetworking;
import com.feel.gems.client.entity.DomainCreatureRenderer;
import com.feel.gems.client.entity.FriendEntityRenderer;
import com.feel.gems.client.entity.StorybookFoldwingRenderer;
import com.feel.gems.client.entity.UndercardConstructRenderer;
import com.feel.gems.client.friend.FriendClientNetworking;
import com.feel.gems.friend.FriendFeatures;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

/** Campaign-only client networking, effects, and entity renderers. */
public final class SecondChanceClientMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        CasinoClientNetworking.register();
        FriendClientNetworking.register();
        ParticleProviderRegistry.getInstance().register(AdventureParticles.CADENZA_SPOTLIGHT, CadenzaMarkParticle.Provider::new);
        ParticleProviderRegistry.getInstance().register(AdventureParticles.CADENZA_CROSSCUT, CadenzaMarkParticle.Provider::new);
        ParticleProviderRegistry.getInstance().register(AdventureParticles.CADENZA_CHORUS, CadenzaMarkParticle.Provider::new);
        ParticleProviderRegistry.getInstance().register(AdventureParticles.CADENZA_LOCK, CadenzaMarkParticle.Provider::new);
        EntityRendererRegistry.register(UndercardEntities.CONSTRUCT, UndercardConstructRenderer::new);
        EntityRendererRegistry.register(AdventureEntities.STORYBOOK_FOLDWING, StorybookFoldwingRenderer::new);
        EntityRendererRegistry.register(AdventureEntities.INKLING, DomainCreatureRenderer::new);
        EntityRendererRegistry.register(AdventureEntities.REEDRUNNER, DomainCreatureRenderer::new);
        EntityRendererRegistry.register(AdventureEntities.BEACON_GULL, DomainCreatureRenderer::new);
        EntityRendererRegistry.register(AdventureEntities.CHORUS_HARE, DomainCreatureRenderer::new);
        EntityRendererRegistry.register(AdventureEntities.CAIRN_BEETLE, DomainCreatureRenderer::new);
        EntityRendererRegistry.register(AdventureEntities.CATALOG_OWL, DomainCreatureRenderer::new);
        EntityRendererRegistry.register(AdventureEntities.DUST_SKIPPER, DomainCreatureRenderer::new);
        EntityRendererRegistry.register(AdventureEntities.RIVET_CRAB, DomainCreatureRenderer::new);
        EntityRendererRegistry.register(AdventureEntities.SPLITSTONE_RAM, DomainCreatureRenderer::new);
        EntityRendererRegistry.register(FriendFeatures.FRIEND, FriendEntityRenderer::new);
        EntityRendererRegistry.register(BossFeatures.PAINTED_VERDICT, PaintedVerdictRenderer::new);
        EntityRendererRegistry.register(BossFeatures.LAST_ARRIVAL, LastArrivalRenderer::new);
        EntityRendererRegistry.register(BossFeatures.UNBROKEN_PUNCHLINE, UnbrokenPunchlineRenderer::new);
        EntityRendererRegistry.register(BossFeatures.SECOND_MOVE_SOLVER, SecondMoveSolverRenderer::new);
        EntityRendererRegistry.register(BossFeatures.RED_LINE_BETWEEN, RedLineBetweenRenderer::new);
        EntityRendererRegistry.register(BossFeatures.EMPTY_ENCORE, EmptyEncoreRenderer::new);
        EntityRendererRegistry.register(BossFeatures.SIGNAL_ECHO, SignalEchoRenderer::new);
        EntityRendererRegistry.register(BossFeatures.STRAY_CADENZA, StrayCadenzaRenderer::new);
        EntityRendererRegistry.register(BossFeatures.SIGNAL_WARDEN,
                context -> new RegionalSentryRenderer(context, "signal_warden"));
        EntityRendererRegistry.register(BossFeatures.UNDERSTUDY,
                context -> new RegionalSentryRenderer(context, "understudy"));
        EntityRendererRegistry.register(BossFeatures.MARGIN_SHADE,
                context -> new RegionalSentryRenderer(context, "margin_shade"));
        EntityRendererRegistry.register(BossFeatures.WATCHKEEPER,
                context -> new RegionalSentryRenderer(context, "watchkeeper"));
        EntityRendererRegistry.register(BossFeatures.RELAY_PHANTOM,
                context -> new RegionalSentryRenderer(context, "relay_phantom"));
        EntityRendererRegistry.register(BossFeatures.UNCAST,
                context -> new RegionalSentryRenderer(context, "uncast"));
        EntityRendererRegistry.register(BossFeatures.SKETCHGUARD, SketchguardRenderer::new);
    }
}
