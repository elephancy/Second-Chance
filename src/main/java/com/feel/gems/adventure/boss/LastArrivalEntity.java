package com.feel.gems.adventure.boss;

import com.feel.gems.adventure.CampaignRewards;
import com.feel.gems.adventure.LandmarkKey;
import com.feel.gems.adventure.AdventureSounds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** A station encounter: leave the lit track, then follow its late arrival to the annex. */
public final class LastArrivalEntity extends Monster {
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(
            LastArrivalEntity.class, EntityDataSerializers.INT);
    private static final int ARENA_RADIUS = 29;
    private static final int LOCK_TICK = 30;
    private static final int RELEASE_TICK = 50;
    private static final int CYCLE_TICKS = 98;
    private static final double LANE_HALF_WIDTH = 1.2;
    private static final DustParticleOptions WARNING = new DustParticleOptions(0xF5CA6C, 1.1F);
    private static final DustParticleOptions LOCKED = new DustParticleOptions(0xEB7651, 1.25F);

    private final Map<UUID, Integer> presenceTicks = new HashMap<>();
    private final Set<UUID> damagers = new HashSet<>();
    private BlockPos arenaCenter;
    private BlockPos currentStage;
    private int track;
    private int cycleTick;
    private boolean lastlightArena;
    private boolean latePhase;
    private boolean active;
    private boolean defeatedOnce;
    private int rematchReadyAge;

    public LastArrivalEntity(EntityType<? extends LastArrivalEntity> type, Level world) {
        super(type, world);
        setPersistenceRequired();
        setCustomName(Component.literal("The Last Arrival"));
        setCustomNameVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 145.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.FOLLOW_RANGE, 30.0)
                .add(Attributes.ARMOR, 4.0);
    }

    @Override
    protected void registerGoals() {
        // Station tracks, not pathfinding, drive this encounter.
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(PHASE, 0);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel world) || !lastlightArena) return;
        if (arenaCenter == null) arenaCenter = blockPosition();
        if (currentStage == null) currentStage = arenaCenter;
        // A saved mid-fight boss reloads inactive at its original platform.
        if (active || !defeatedOnce) keepOnStage(world);
        if (world.getDifficulty() == Difficulty.PEACEFUL) {
            stop(world);
            return;
        }
        if (tickCount % 20 == 0) {
            List<ServerPlayer> nearby = nearbyPlayers(world);
            if (!active) {
                if (!defeatedOnce && nearby.stream().anyMatch(player ->
                        player.distanceToSqr(Vec3.atCenterOf(arenaCenter)) < 12 * 12)) {
                    start(world, nearby);
                }
            } else if (nearby.isEmpty()) {
                stop(world);
            } else {
                for (ServerPlayer player : nearby) {
                    presenceTicks.merge(player.getUUID(), 20, Integer::sum);
                }
            }
        }
        if (!active) return;
        if (!latePhase && getHealth() <= getMaxHealth() * 0.5F) {
            enterLatePhase(world);
        }
        if (cycleTick < LOCK_TICK && cycleTick % 5 == 0) {
            chooseTrack(nearbyPlayers(world));
        }
        if (cycleTick < RELEASE_TICK && cycleTick % 3 == 0) {
            drawTrack(world, cycleTick < LOCK_TICK ? WARNING : LOCKED);
        }
        if (cycleTick == LOCK_TICK) {
            announce(nearbyPlayers(world), "Platform locked. Step off the lit track.");
            BossAudio.tell(nearbyPlayers(world), AdventureSounds.LASTLIGHT_LOCK,
                    0.9F, latePhase ? 0.65F : 0.85F);
        }
        if (cycleTick == RELEASE_TICK) {
            drawTrack(world, LOCKED);
            strike(world);
        }
        if (cycleTick == RELEASE_TICK + 2) {
            announce(nearbyPlayers(world), "The platform is clear. Close in now.");
        }
        cycleTick++;
        if (cycleTick >= CYCLE_TICKS) {
            cycleTick = 0;
            chooseTrack(nearbyPlayers(world));
        }
        syncPhase();
    }

    @Override
    public boolean hurtServer(ServerLevel world, DamageSource source, float amount) {
        if (!active || !lastlightArena) return false;
        Entity attacker = source.getEntity();
        if (attacker instanceof ServerPlayer player) {
            if (arenaCenter == null || player.distanceToSqr(Vec3.atCenterOf(arenaCenter))
                    > (ARENA_RADIUS + 4) * (ARENA_RADIUS + 4)) return false;
            damagers.add(player.getUUID());
        }
        float multiplier = cycleTick > RELEASE_TICK && cycleTick < CYCLE_TICKS ? 1.0F : 0.3F;
        return super.hurtServer(world, source, Math.min(amount, 16.0F) * multiplier);
    }

    @Override
    public void die(DamageSource source) {
        if (lastlightArena && level() instanceof ServerLevel world) {
            Set<UUID> eligibleIds = BossParticipation.eligibleIds(presenceTicks, damagers);
            List<ServerPlayer> eligible = CampaignRewards.onlinePlayers(world.getServer(), eligibleIds);
            if (!eligibleIds.isEmpty()) {
                var victory = CampaignRewards.recordVictoryForIds(world.getServer(), eligibleIds, LandmarkKey.LASTLIGHT);
                CampaignSignatures.deliver(eligible);
                CampaignRewards.deliverRematchSupplies(eligible, LandmarkKey.LASTLIGHT, victory);
            }
            defeatedOnce = true;
            active = false;
            latePhase = false;
            cycleTick = 0;
            presenceTicks.clear();
            damagers.clear();
            rematchReadyAge = tickCount + 100;
            setHealth(getMaxHealth());
            if (arenaCenter != null) {
                currentStage = arenaCenter;
                keepOnStage(world);
            }
            syncPhase();
            announce(nearbyPlayers(world), "The station falls quiet. Speak to the clock for another departure.");
            return;
        }
        super.die(source);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level().isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer) || !lastlightArena || !defeatedOnce
                || tickCount < rematchReadyAge || distanceToSqr(player) > 36.0
                || player.isSpectator() || active) return InteractionResult.PASS;
        if (level().getDifficulty() == Difficulty.PEACEFUL) {
            serverPlayer.sendSystemMessage(Component.literal("The timetable is still in peaceful worlds.")
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.SUCCESS_SERVER;
        }
        if (!start((ServerLevel) level(), nearbyPlayers((ServerLevel) level()))) {
            serverPlayer.sendSystemMessage(Component.literal(
                    "Clear the clock's platform before another departure.")
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.SUCCESS_SERVER;
        }
        serverPlayer.sendSystemMessage(Component.literal("The next departure is yours.")
                .withStyle(ChatFormatting.GOLD), true);
        return InteractionResult.SUCCESS_SERVER;
    }

    private boolean start(ServerLevel world, List<ServerPlayer> nearby) {
        if (arenaCenter == null) arenaCenter = blockPosition();
        if (!world.getBlockState(arenaCenter).isAir()
                || !world.getBlockState(arenaCenter.above()).isAir()) return false;
        currentStage = arenaCenter;
        keepOnStage(world);
        active = true;
        latePhase = false;
        cycleTick = 0;
        chooseTrack(nearby);
        syncPhase();
        announce(nearby, "The Last Arrival boards. Leave the marked platform before the bell.");
        return true;
    }

    private void enterLatePhase(ServerLevel world) {
        // A lodestone under the annex threshold survives template rotation.
        BlockPos lateSpot = null;
        search: for (int dx = -23; dx <= 23; dx++) {
            for (int dz = -23; dz <= 23; dz++) {
                BlockPos position = arenaCenter.offset(dx, 0, dz);
                if (world.getBlockState(position.below()).is(Blocks.LODESTONE)
                        && world.getBlockState(position).isAir()
                        && world.getBlockState(position.above()).isAir()) {
                    lateSpot = position.immutable();
                    break search;
                }
            }
        }
        latePhase = true;
        cycleTick = 0;
        world.sendParticles(WARNING, getX(), getY() + 1.0, getZ(), 24, 0.6, 0.8, 0.6, 0.02);
        if (lateSpot != null) {
            snapTo(lateSpot.getX() + 0.5, lateSpot.getY(),
                    lateSpot.getZ() + 0.5, getYRot(), getXRot());
            currentStage = lateSpot;
            setDeltaMovement(Vec3.ZERO);
        }
        world.sendParticles(LOCKED, getX(), getY() + 1.0, getZ(), 24, 0.6, 0.8, 0.6, 0.02);
        announce(nearbyPlayers(world), lateSpot == null
                ? "The annex is blocked. The tracks turn here instead."
                : "Late arrival at the annex. The tracks run the other way now.");
        chooseTrack(nearbyPlayers(world));
    }

    private void chooseTrack(List<ServerPlayer> players) {
        if (arenaCenter == null || players.isEmpty()) return;
        ServerPlayer nearest = players.stream()
                .min((a, b) -> Double.compare(a.distanceToSqr(this), b.distanceToSqr(this)))
                .orElseThrow();
        int[] offsets = {-9, 0, 9};
        double axis = latePhase ? nearest.getZ() - arenaCenter.getZ()
                : nearest.getX() - arenaCenter.getX();
        int best = 0;
        for (int i = 1; i < offsets.length; i++) {
            if (Math.abs(axis - offsets[i]) < Math.abs(axis - offsets[best])) best = i;
        }
        track = best;
    }

    private void drawTrack(ServerLevel world, DustParticleOptions particle) {
        if (arenaCenter == null) return;
        int axis = (latePhase ? arenaCenter.getZ() : arenaCenter.getX()) + new int[]{-9, 0, 9}[track];
        for (int step = -14; step <= 14; step += 2) {
            double x = latePhase ? arenaCenter.getX() + step + 0.5 : axis + 0.5;
            double z = latePhase ? axis + 0.5 : arenaCenter.getZ() + step + 0.5;
            world.sendParticles(particle, x, arenaCenter.getY() + 0.3, z,
                    1, 0.06, 0.02, 0.06, 0);
        }
    }

    private void strike(ServerLevel world) {
        if (arenaCenter == null) return;
        double axis = (latePhase ? arenaCenter.getZ() : arenaCenter.getX())
                + new int[]{-9, 0, 9}[track] + 0.5;
        for (ServerPlayer player : nearbyPlayers(world)) {
            if (Math.abs(player.getY() - arenaCenter.getY()) > 3.0) continue;
            double across = latePhase ? player.getZ() : player.getX();
            double along = latePhase ? player.getX() - arenaCenter.getX()
                    : player.getZ() - arenaCenter.getZ();
            if (Math.abs(across - axis) <= LANE_HALF_WIDTH && Math.abs(along) <= 14.5) {
                player.hurtServer(world, world.damageSources().mobAttack(this), latePhase ? 9.0F : 7.0F);
            }
        }
        BossAudio.tell(nearbyPlayers(world), AdventureSounds.LASTLIGHT_STRIKE,
                0.7F, latePhase ? 1.15F : 0.95F);
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

    private void keepOnStage(ServerLevel world) {
        if (currentStage == null || !world.getBlockState(currentStage).isAir()
                || !world.getBlockState(currentStage.above()).isAir()) return;
        Vec3 center = Vec3.atBottomCenterOf(currentStage);
        if (position().distanceToSqr(center) > 0.01) {
            snapTo(center.x, center.y, center.z, getYRot(), getXRot());
        }
        setDeltaMovement(Vec3.ZERO);
    }

    private void stop(ServerLevel world) {
        active = false;
        latePhase = false;
        cycleTick = 0;
        presenceTicks.clear();
        damagers.clear();
        setHealth(getMaxHealth());
        currentStage = arenaCenter;
        keepOnStage(world);
        syncPhase();
    }

    private void syncPhase() {
        getEntityData().set(PHASE, !active ? 0 : latePhase ? 2 : 1);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput view) {
        super.readAdditionalSaveData(view);
        lastlightArena = view.getBooleanOr("LastlightArena", false);
        defeatedOnce = view.getBooleanOr("DefeatedOnce", false);
        int x = view.getIntOr("ArenaX", Integer.MIN_VALUE);
        int y = view.getIntOr("ArenaY", Integer.MIN_VALUE);
        int z = view.getIntOr("ArenaZ", Integer.MIN_VALUE);
        arenaCenter = x == Integer.MIN_VALUE || y == Integer.MIN_VALUE || z == Integer.MIN_VALUE
                ? null : new BlockPos(x, y, z);
        currentStage = arenaCenter;
        active = false;
        latePhase = false;
        cycleTick = 0;
        setHealth(getMaxHealth());
        syncPhase();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput view) {
        super.addAdditionalSaveData(view);
        view.putBoolean("LastlightArena", lastlightArena);
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
