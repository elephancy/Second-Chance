package com.feel.gems.adventure.boss;

import com.feel.gems.adventure.CampaignRewards;
import com.feel.gems.adventure.LandmarkKey;
import com.feel.gems.particle.ModParticles;
import com.feel.gems.adventure.AdventureSounds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.ChatFormatting;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.Level;

/** A shelter fight: reach the threatened wing before its red line closes. */
public final class RedLineBetweenEntity extends Monster {
    private static final int ARENA_RADIUS = 26;
    private static final int LOCK_TICK = 46;
    private static final int STRIKE_TICK = 64;
    private static final int NORMAL_RECOVERY = 39;
    private static final int RESCUE_RECOVERY = 72;
    private static final DustParticleOptions WARNING = new DustParticleOptions(0xD95B58, 1.15F);
    private static final DustParticleOptions SAFE = new DustParticleOptions(0x8ECE85, 1.2F);

    private final RedlineGuardPlan plan = new RedlineGuardPlan();
    private final Map<UUID, Integer> presenceTicks = new HashMap<>();
    private final Set<UUID> contributors = new HashSet<>();
    private BlockPos arenaCenter;
    private int cycleTick;
    private int rematchReadyAge;
    private boolean redlineArena;
    private boolean active;
    private boolean defeatedOnce;

    public RedLineBetweenEntity(EntityType<? extends RedLineBetweenEntity> type, Level world) {
        super(type, world);
        setPersistenceRequired();
        setCustomName(Component.literal("The Red Line Between"));
        setCustomNameVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 175.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.FOLLOW_RANGE, 30.0)
                .add(Attributes.ARMOR, 6.0);
    }

    @Override
    protected void registerGoals() {
        // Shelters and their marked lines define the fight.
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel world) || !redlineArena) return;
        if (arenaCenter == null) arenaCenter = blockPosition();
        // Ordinary melee knockback can move a stationary guard away from the
        // authored shelter lines. Keep it on the intact arena anchor.
        if (active || !defeatedOnce) keepAtArenaCenter(world);
        if (world.getDifficulty() == Difficulty.PEACEFUL) {
            stop();
            return;
        }
        if (tickCount % 20 == 0) {
            List<ServerPlayer> nearby = nearbyPlayers(world);
            if (!active) {
                if (!defeatedOnce && nearby.stream().anyMatch(player ->
                        player.distanceToSqr(Vec3.atCenterOf(arenaCenter)) < 12 * 12)) {
                    start(nearby);
                }
            } else if (nearby.isEmpty()) {
                stop();
            } else {
                for (ServerPlayer player : nearby) {
                    presenceTicks.merge(player.getUUID(), 20, Integer::sum);
                }
            }
        }
        if (!active) return;
        if (cycleTick < LOCK_TICK && !plan.broken() && cycleTick % 2 == 0) {
            tryRescue(world);
        }
        if (cycleTick < STRIKE_TICK && cycleTick % 3 == 0) {
            drawLine(world, plan.broken() ? SAFE : WARNING);
        }
        if (cycleTick == LOCK_TICK) {
            plan.lock();
            if (!plan.broken()) announce(nearbyPlayers(world), "The line closes. Leave the red path.");
            BossAudio.tell(nearbyPlayers(world), AdventureSounds.REDLINE_LOCK, 0.8F, 0.65F);
        }
        if (cycleTick == STRIKE_TICK) {
            if (!plan.broken()) {
                strike(world);
                announce(nearbyPlayers(world), "The line passed. The guard is open now.");
            }
        }
        cycleTick++;
        if (cycleTick >= STRIKE_TICK + (plan.broken() ? RESCUE_RECOVERY : NORMAL_RECOVERY)) {
            cycleTick = 0;
            chooseWing(nearbyPlayers(world));
        }
    }

    @Override
    public boolean hurtServer(ServerLevel world, DamageSource source, float amount) {
        if (!active || !redlineArena) return false;
        Entity attacker = source.getEntity();
        if (attacker instanceof ServerPlayer player) {
            if (arenaCenter == null || player.distanceToSqr(Vec3.atCenterOf(arenaCenter))
                    > (ARENA_RADIUS + 4) * (ARENA_RADIUS + 4)) return false;
            contributors.add(player.getUUID());
        }
        float multiplier = plan.broken() || cycleTick > STRIKE_TICK ? 1.0F : 0.25F;
        return super.hurtServer(world, source, Math.min(amount, 16.0F) * multiplier);
    }

    @Override
    public void die(DamageSource source) {
        if (redlineArena && level() instanceof ServerLevel world) {
            Set<UUID> eligibleIds = BossParticipation.eligibleIds(presenceTicks, contributors);
            List<ServerPlayer> eligible = CampaignRewards.onlinePlayers(world.getServer(), eligibleIds);
            if (!eligibleIds.isEmpty()) {
                var victory = CampaignRewards.recordVictoryForIds(world.getServer(), eligibleIds,
                        LandmarkKey.REDLINE);
                CampaignSignatures.deliver(eligible);
                if (RedlineRestoration.reveal(world, arenaCenter) > 0) {
                    announce(eligible, "Every shelter is open now.");
                }
                CampaignRewards.deliverRematchSupplies(eligible, LandmarkKey.REDLINE, victory);
            }
            defeatedOnce = true;
            stop();
            rematchReadyAge = tickCount + 100;
            setHealth(getMaxHealth());
            if (arenaCenter != null) {
                snapTo(arenaCenter.getX() + 0.5, arenaCenter.getY(),
                        arenaCenter.getZ() + 0.5, getYRot(), getXRot());
            }
            announce(nearbyPlayers(world), "Redline is quiet. Speak to the guard for another round.");
            return;
        }
        super.die(source);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level().isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer) || !redlineArena || !defeatedOnce
                || active || tickCount < rematchReadyAge || distanceToSqr(player) > 36.0
                || player.isSpectator()) return InteractionResult.PASS;
        if (level().getDifficulty() == Difficulty.PEACEFUL) {
            serverPlayer.sendSystemMessage(Component.literal("The shelter is quiet in peaceful worlds.")
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.SUCCESS_SERVER;
        }
        start(nearbyPlayers((ServerLevel) level()));
        return InteractionResult.SUCCESS_SERVER;
    }

    private void start(List<ServerPlayer> nearby) {
        if (arenaCenter == null) arenaCenter = blockPosition();
        setHealth(getMaxHealth());
        active = true;
        cycleTick = 0;
        plan.reset();
        presenceTicks.clear();
        contributors.clear();
        chooseWing(nearby);
        announce(nearby, "Reach the gold shelter before its red line closes.");
    }

    private void stop() {
        active = false;
        cycleTick = 0;
        plan.reset();
        presenceTicks.clear();
        contributors.clear();
    }

    private void chooseWing(List<ServerPlayer> players) {
        if (players.isEmpty()) return;
        ServerPlayer nearest = players.stream()
                .min((a, b) -> Double.compare(a.distanceToSqr(this), b.distanceToSqr(this)))
                .orElseThrow();
        RedlineGuardPlan.Wing preferred = RedlineGuardPlan.Wing.nearest(
                nearest.getX() - arenaCenter.getX() - 0.5,
                nearest.getZ() - arenaCenter.getZ() - 0.5);
        RedlineGuardPlan.Wing wing = plan.begin(preferred);
        announce(players, "The " + wing.label + " shelter is marked. Someone can reach it.");
    }

    private void tryRescue(ServerLevel world) {
        RedlineGuardPlan.Wing wing = plan.wing();
        if (wing == null) return;
        BlockPos pad = arenaCenter.offset(wing.dx * 9, -1, wing.dz * 9);
        if (!world.getBlockState(pad).is(Blocks.CONCRETE.yellow())
                && !world.getBlockState(pad).is(Blocks.CONCRETE.lime())) return;
        for (ServerPlayer player : nearbyPlayers(world)) {
            if (Math.abs(player.getY() - arenaCenter.getY()) > 2.0) continue;
            double dx = player.getX() - pad.getX() - 0.5;
            double dz = player.getZ() - pad.getZ() - 0.5;
            if (dx * dx + dz * dz > 1.25 * 1.25 || !plan.reach(wing)) continue;
            contributors.add(player.getUUID());
            List<ServerPlayer> protectedPlayers = nearbyPlayers(world);
            for (ServerPlayer ally : protectedPlayers) {
                ally.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 80, 0));
            }
            world.sendParticles(ModParticles.GEM_RING, pad.getX() + 0.5,
                    pad.getY() + 1.2, pad.getZ() + 0.5, 16, 0.6, 0.3, 0.6, 0.01);
            world.playSound(null, pad, AdventureSounds.REDLINE_SHELTER, SoundSource.HOSTILE, 0.8F, 1.15F);
            announce(protectedPlayers, player.getName().getString()
                    + " reached the shelter. The guard is open!");
            return;
        }
    }

    private void drawLine(ServerLevel world, DustParticleOptions dust) {
        RedlineGuardPlan.Wing wing = plan.wing();
        if (wing == null) return;
        for (int step = 2; step <= 11; step++) {
            world.sendParticles(dust, arenaCenter.getX() + 0.5 + wing.dx * step,
                    arenaCenter.getY() + 0.25,
                    arenaCenter.getZ() + 0.5 + wing.dz * step,
                    1, 0.08, 0.03, 0.08, 0);
        }
        world.sendParticles(dust, arenaCenter.getX() + 0.5 + wing.dx * 9,
                arenaCenter.getY() + 1.1,
                arenaCenter.getZ() + 0.5 + wing.dz * 9,
                3, 0.2, 0.25, 0.2, 0);
    }

    private void keepAtArenaCenter(ServerLevel world) {
        if (arenaCenter == null
                || !world.getBlockState(arenaCenter.below()).is(Blocks.LODESTONE)
                || !world.getBlockState(arenaCenter).isAir()
                || !world.getBlockState(arenaCenter.above()).isAir()) return;
        Vec3 center = Vec3.atBottomCenterOf(arenaCenter);
        if (position().distanceToSqr(center) > 0.01) {
            snapTo(center.x, center.y, center.z, getYRot(), getXRot());
        }
        setDeltaMovement(Vec3.ZERO);
    }

    private void strike(ServerLevel world) {
        RedlineGuardPlan.Wing wing = plan.wing();
        if (wing == null) return;
        for (ServerPlayer player : nearbyPlayers(world)) {
            if (Math.abs(player.getY() - arenaCenter.getY()) > 2.0) continue;
            double dx = player.getX() - arenaCenter.getX() - 0.5;
            double dz = player.getZ() - arenaCenter.getZ() - 0.5;
            double along = dx * wing.dx + dz * wing.dz;
            double across = dx * wing.dz - dz * wing.dx;
            if (along >= 0 && along <= 11.5 && Math.abs(across) <= 1.25) {
                player.hurtServer(world, world.damageSources().mobAttack(this), 8.0F);
            }
        }
        BossAudio.tell(nearbyPlayers(world), AdventureSounds.REDLINE_STRIKE, 0.7F, 0.7F);
    }

    private List<ServerPlayer> nearbyPlayers(ServerLevel world) {
        if (arenaCenter == null) return List.of();
        return new ArrayList<>(world.getEntitiesOfClass(ServerPlayer.class,
                new AABB(arenaCenter).inflate(ARENA_RADIUS),
                player -> player.isAlive() && !player.isSpectator()));
    }

    private void announce(List<ServerPlayer> players, String message) {
        for (ServerPlayer player : players) {
            player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.GOLD), true);
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput view) {
        super.readAdditionalSaveData(view);
        redlineArena = view.getBooleanOr("RedlineArena", false);
        defeatedOnce = view.getBooleanOr("DefeatedOnce", false);
        int x = view.getIntOr("ArenaX", Integer.MIN_VALUE);
        int y = view.getIntOr("ArenaY", Integer.MIN_VALUE);
        int z = view.getIntOr("ArenaZ", Integer.MIN_VALUE);
        arenaCenter = x == Integer.MIN_VALUE || y == Integer.MIN_VALUE || z == Integer.MIN_VALUE
                ? null : new BlockPos(x, y, z);
        stop();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput view) {
        super.addAdditionalSaveData(view);
        view.putBoolean("RedlineArena", redlineArena);
        view.putBoolean("DefeatedOnce", defeatedOnce);
        if (arenaCenter != null) {
            view.putInt("ArenaX", arenaCenter.getX());
            view.putInt("ArenaY", arenaCenter.getY());
            view.putInt("ArenaZ", arenaCenter.getZ());
        }
    }

    @Override
    public boolean isPersistenceRequired() {
        return true;
    }
}
