package com.feel.gems.entity;

import com.feel.gems.adventure.AdventureSounds;
import com.feel.gems.adventure.AdventureItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Small, peaceful residents of the later friend domains. Each type fixes its identity at registration. */
public final class DomainCreatureEntity extends PathfinderMob {
    private static final long REED_HARVEST_COOLDOWN = 20L * 60 * 5;
    private static final EntityDataAccessor<Boolean> REED_READY = SynchedEntityData.defineId(
            DomainCreatureEntity.class, EntityDataSerializers.BOOLEAN);
    public enum Kind {
        INKLING(0x253D68, "inkling"),
        REEDRUNNER(0x63A59B, "reedrunner"),
        BEACON_GULL(0xF3DB89, "beacon_gull"),
        CHORUS_HARE(0xD19B70, "chorus_hare"),
        CAIRN_BEETLE(0x7391AE, "cairn_beetle"),
        CATALOG_OWL(0xB7AD83, "catalog_owl"),
        DUST_SKIPPER(0xC78964, "dust_skipper"),
        RIVET_CRAB(0xBE784C, "rivet_crab"),
        SPLITSTONE_RAM(0xC4C2B5, "splitstone_ram");

        public final int color;
        public final String id;

        Kind(int color, String id) {
            this.color = color;
            this.id = id;
        }
    }

    private final Kind kind;
    private int pulseCooldown;
    private int interactionCooldown;
    private long lastReedHarvest = Long.MIN_VALUE;

    public DomainCreatureEntity(EntityType<? extends DomainCreatureEntity> type, Level level, Kind kind) {
        super(type, level);
        this.kind = kind;
        pulseCooldown = 35 + getRandom().nextInt(60);
        if (kind == Kind.REEDRUNNER) {
            goalSelector.addGoal(0, new TemptGoal(this, 1.05,
                    stack -> stack.is(Items.SEAGRASS), false));
        }
    }

    public Kind kind() {
        return kind;
    }

    public boolean reedReady() {
        return kind == Kind.REEDRUNNER && getEntityData().get(REED_READY);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(REED_READY, false);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput view) {
        super.readAdditionalSaveData(view);
        lastReedHarvest = view.getLongOr("ReedHarvestGameTime", Long.MIN_VALUE);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput view) {
        super.addAdditionalSaveData(view);
        if (kind == Kind.REEDRUNNER && lastReedHarvest != Long.MIN_VALUE) {
            view.putLong("ReedHarvestGameTime", lastReedHarvest);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 8.0)
                .add(Attributes.MOVEMENT_SPEED, 0.18);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new WaterAvoidingRandomStrollGoal(this, 0.75, 0.7F));
        goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 5.0F));
        goalSelector.addGoal(3, new RandomLookAroundGoal(this));
    }

    @Override
    public boolean checkSpawnRules(LevelAccessor level, EntitySpawnReason reason) {
        var feet = blockPosition();
        return super.checkSpawnRules(level, reason)
                && level.getBlockState(feet.below()).isSolid()
                && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty();
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        if (interactionCooldown > 0) interactionCooldown--;
        if (--pulseCooldown > 0) return;
        pulseCooldown = switch (kind) {
            case BEACON_GULL, CATALOG_OWL -> 100 + getRandom().nextInt(45);
            case REEDRUNNER, DUST_SKIPPER, CHORUS_HARE -> 65 + getRandom().nextInt(35);
            default -> 85 + getRandom().nextInt(45);
        };
        if (kind == Kind.REEDRUNNER) updateReedrunner(level);
        // One tiny local event per resident, never a player or block scan.
        switch (kind) {
            case REEDRUNNER, DUST_SKIPPER, CHORUS_HARE -> {
                if (onGround()) getJumpControl().jump();
            }
            case CAIRN_BEETLE, CATALOG_OWL -> setYRot(getYRot() + 45.0F);
            case RIVET_CRAB -> getNavigation().stop();
            case SPLITSTONE_RAM -> {
                if (onGround() && getRandom().nextBoolean()) getJumpControl().jump();
            }
            default -> { }
        }
        level.sendParticles(new DustParticleOptions(kind.color, 0.65F),
                getX(), getY() + getBbHeight() * 0.7, getZ(),
                kind == Kind.BEACON_GULL ? 5 : 2, 0.16, 0.1, 0.16, 0.002);
        if (kind != Kind.REEDRUNNER && getRandom().nextInt(8) == 0) {
            level.playSound(null, blockPosition(), cue(), SoundSource.NEUTRAL,
                    0.2F, 0.92F + getRandom().nextFloat() * 0.16F);
        }
    }

    private void updateReedrunner(ServerLevel level) {
        boolean ready = nearWater(level) && harvestCooldownFinished(level.getGameTime());
        boolean previous = reedReady();
        if (ready == previous) return;
        getEntityData().set(REED_READY, ready);
        if (ready) {
            level.sendParticles(new DustParticleOptions(0xB9EBD9, 0.75F),
                    getX(), getY() + 0.55, getZ(), 7, 0.24, 0.16, 0.24, 0.004);
            level.playSound(null, blockPosition(), AdventureSounds.CREATURE_REED,
                    SoundSource.NEUTRAL, 0.28F, 0.84F);
        }
    }

    private boolean nearWater(ServerLevel level) {
        BlockPos pos = blockPosition();
        return level.isRainingAt(pos.above())
                || level.getFluidState(pos).is(FluidTags.WATER)
                || level.getFluidState(pos.below()).is(FluidTags.WATER)
                || level.getFluidState(pos.north()).is(FluidTags.WATER)
                || level.getFluidState(pos.south()).is(FluidTags.WATER)
                || level.getFluidState(pos.east()).is(FluidTags.WATER)
                || level.getFluidState(pos.west()).is(FluidTags.WATER);
    }

    private boolean harvestCooldownFinished(long now) {
        return lastReedHarvest == Long.MIN_VALUE || now < lastReedHarvest
                || now - lastReedHarvest >= REED_HARVEST_COOLDOWN;
    }

    private InteractionResult harvestReed(ServerLevel level, ServerPlayer player) {
        if (!nearWater(level)) {
            player.sendSystemMessage(Component.translatable("message.gems.creature.reedrunner.dry"), true);
            return InteractionResult.SUCCESS_SERVER;
        }
        if (!harvestCooldownFinished(level.getGameTime())) {
            player.sendSystemMessage(Component.translatable("message.gems.creature.reedrunner.resting"), true);
            return InteractionResult.SUCCESS_SERVER;
        }
        lastReedHarvest = level.getGameTime();
        getEntityData().set(REED_READY, false);
        ItemStack plume = new ItemStack(AdventureItems.RIVERWARD_PLUME);
        if (!player.addItem(plume)) player.spawnAtLocation(level, plume);
        player.sendSystemMessage(Component.translatable("message.gems.creature.reedrunner.harvest"), true);
        level.sendParticles(new DustParticleOptions(0xB9EBD9, 0.95F),
                getX(), getY() + 0.5, getZ(), 12, 0.27, 0.2, 0.27, 0.008);
        level.playSound(null, blockPosition(), AdventureSounds.CREATURE_REED,
                SoundSource.NEUTRAL, 0.52F, 1.08F);
        if (onGround()) getJumpControl().jump();
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || player.isSpectator()) return InteractionResult.PASS;
        if (level().isClientSide()) return InteractionResult.SUCCESS;
        if (!(level() instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        if (interactionCooldown > 0) return InteractionResult.SUCCESS_SERVER;
        interactionCooldown = 30;
        if (kind == Kind.REEDRUNNER && player.getItemInHand(hand).isEmpty()) {
            return harvestReed(level, serverPlayer);
        }
        serverPlayer.sendSystemMessage(Component.translatable("message.gems.creature." + kind.id), true);
        level.sendParticles(new DustParticleOptions(kind.color, 0.95F),
                getX(), getY() + getBbHeight() * 0.8, getZ(), 10, 0.25, 0.2, 0.25, 0.01);
        if (kind == Kind.REEDRUNNER || kind == Kind.DUST_SKIPPER || kind == Kind.CHORUS_HARE) {
            if (onGround()) getJumpControl().jump();
        }
        level.playSound(null, blockPosition(), cue(), SoundSource.NEUTRAL, 0.38F,
                0.92F + (kind.ordinal() % 3) * 0.08F);
        return InteractionResult.SUCCESS_SERVER;
    }

    private SoundEvent cue() {
        return switch (kind) {
            case INKLING, CATALOG_OWL -> AdventureSounds.CREATURE_PAPER;
            case RIVET_CRAB -> AdventureSounds.CREATURE_CLOCKWORK;
            case REEDRUNNER -> AdventureSounds.CREATURE_REED;
            case BEACON_GULL, CHORUS_HARE, DUST_SKIPPER -> AdventureSounds.CREATURE_BREATH;
            case CAIRN_BEETLE, SPLITSTONE_RAM -> AdventureSounds.CREATURE_STONE;
        };
    }
}
