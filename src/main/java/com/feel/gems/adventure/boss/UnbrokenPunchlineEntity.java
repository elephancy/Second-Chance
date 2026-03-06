package com.feel.gems.adventure.boss;

import com.feel.gems.adventure.CampaignRewards;
import com.feel.gems.adventure.LandmarkKey;
import com.feel.gems.adventure.AdventureSounds;
import java.util.ArrayList;
import java.util.Comparator;
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
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Three physical stages and a spotlight that learns repeated player attacks. */
public final class UnbrokenPunchlineEntity extends Monster {
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(
            UnbrokenPunchlineEntity.class, EntityDataSerializers.INT);
    private static final int ARENA_RADIUS = 42;
    private static final int LOCK_TICK = 36;
    private static final int STRIKE_TICK = 58;
    private static final int NORMAL_CYCLE = 122;
    private static final int ENCORE_CYCLE = 106;
    private static final DustParticleOptions SPOTLIGHT = new DustParticleOptions(0xF3D975, 1.15F);
    private static final DustParticleOptions LOCKED = new DustParticleOptions(0xD76491, 1.25F);

    private final PunchlinePattern pattern = new PunchlinePattern();
    private final Map<UUID, Integer> presenceTicks = new HashMap<>();
    private final Set<UUID> damagers = new HashSet<>();
    private BlockPos arenaCenter;
    private BlockPos currentStage;
    private final BlockPos[] stageMarks = new BlockPos[3];
    private UUID targetId;
    private UUID previousTargetId;
    private Vec3 spot;
    private PunchlinePattern.Counter counter = PunchlinePattern.Counter.NONE;
    private int stageIndex;
    private int cycleTick;
    private int rematchReadyAge;
    private boolean rehearsalArena;
    private boolean active;
    private boolean encore;
    private boolean defeatedOnce;

    public UnbrokenPunchlineEntity(EntityType<? extends UnbrokenPunchlineEntity> type, Level world) {
        super(type, world);
        setPersistenceRequired();
        setCustomName(Component.literal("The Unbroken Punchline"));
        setCustomNameVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 160.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.FOLLOW_RANGE, 42.0)
                .add(Attributes.ARMOR, 4.0);
    }

    @Override
    protected void registerGoals() {
        // Stage changes and target selection are authored by the encounter.
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(PHASE, 0);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel world) || !rehearsalArena) return;
        if (arenaCenter == null) arenaCenter = blockPosition();
        // A normal melee hit may add velocity even though this boss has no
        // walking goal. Return it to the current physical stage on the next
        // tick so knockback cannot send it off a balcony or into the terrain.
        if (active) keepOnStage(world);
        if (world.getDifficulty() == Difficulty.PEACEFUL) {
            stop();
            return;
        }
        if (tickCount % 20 == 0) {
            List<ServerPlayer> nearby = nearbyPlayers(world);
            if (!active) {
                if (!defeatedOnce && nearby.stream().anyMatch(player ->
                        player.distanceToSqr(Vec3.atCenterOf(arenaCenter)) < 14 * 14)) {
                    start(world, nearby);
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
        if (!encore && getHealth() <= getMaxHealth() * 0.5F) {
            encore = true;
            announce(nearbyPlayers(world), "Encore. The lights move faster now.");
            syncPhase();
        }
        if (cycleTick < LOCK_TICK && cycleTick % 5 == 0) {
            ServerPlayer target = target(world);
            if (target == null) selectTarget(nearbyPlayers(world));
            else spot = target.position();
        }
        if (spot != null && cycleTick < STRIKE_TICK && cycleTick % 3 == 0) {
            drawCircle(world, spot, spotRadius(), cycleTick < LOCK_TICK ? SPOTLIGHT : LOCKED);
            if (counter == PunchlinePattern.Counter.CLOSE) {
                drawCircle(world, position(), 3.5, LOCKED);
            }
        }
        if (cycleTick == LOCK_TICK) {
            announce(nearbyPlayers(world), counter == PunchlinePattern.Counter.CLOSE
                    ? "The rush is on cue. Leave the stage edge and the lit mark."
                    : counter == PunchlinePattern.Counter.PROJECTILE
                    ? "The same shot again. Clear the wider spotlight."
                    : "Spotlight locked. Step out of the circle.");
            BossAudio.tell(nearbyPlayers(world), AdventureSounds.REHEARSAL_LOCK, 0.8F, 1.25F);
        }
        if (cycleTick == STRIKE_TICK) {
            strike(world);
            BossAudio.tell(nearbyPlayers(world), AdventureSounds.REHEARSAL_STRIKE, 0.7F, 1.3F);
        }
        if (cycleTick == STRIKE_TICK + 2) {
            announce(nearbyPlayers(world), "The light is down. Take your opening.");
        }
        cycleTick++;
        if (cycleTick >= (encore ? ENCORE_CYCLE : NORMAL_CYCLE)) {
            stageIndex = (stageIndex + 1) % stageMarks.length;
            moveStage(world);
            cycleTick = 0;
            selectTarget(nearbyPlayers(world));
            announce(nearbyPlayers(world), "New stage. Watch where the light lands.");
        }
    }

    @Override
    public boolean hurtServer(ServerLevel world, DamageSource source, float amount) {
        if (!active || !rehearsalArena) return false;
        Entity attacker = source.getEntity();
        if (attacker instanceof ServerPlayer player) {
            if (arenaCenter == null || player.distanceToSqr(Vec3.atCenterOf(arenaCenter))
                    > (ARENA_RADIUS + 4) * (ARENA_RADIUS + 4)) return false;
            damagers.add(player.getUUID());
            pattern.record(player.getUUID(), source.getDirectEntity() instanceof Projectile
                    ? PunchlinePattern.Attack.PROJECTILE : PunchlinePattern.Attack.CLOSE);
        }
        float multiplier = cycleTick > STRIKE_TICK && cycleTick < (encore ? ENCORE_CYCLE : NORMAL_CYCLE)
                ? 1.0F : 0.35F;
        return super.hurtServer(world, source, Math.min(amount, 16.0F) * multiplier);
    }

    @Override
    public void die(DamageSource source) {
        if (rehearsalArena && level() instanceof ServerLevel world) {
            Set<UUID> eligibleIds = BossParticipation.eligibleIds(presenceTicks, damagers);
            List<ServerPlayer> eligible = CampaignRewards.onlinePlayers(world.getServer(), eligibleIds);
            if (!eligibleIds.isEmpty()) {
                var victory = CampaignRewards.recordVictoryForIds(world.getServer(), eligibleIds,
                        LandmarkKey.REHEARSAL);
                CampaignSignatures.deliver(eligible);
                if (RehearsalRestoration.relight(world, stageMarks) > 0) {
                    announce(eligible, "The footlights stay warm after the curtain falls.");
                }
                CampaignRewards.deliverRematchSupplies(eligible, LandmarkKey.REHEARSAL, victory);
            }
            defeatedOnce = true;
            stop();
            rematchReadyAge = tickCount + 100;
            setHealth(getMaxHealth());
            if (arenaCenter != null) {
                snapTo(arenaCenter.getX() + 0.5, arenaCenter.getY(),
                        arenaCenter.getZ() + 0.5, getYRot(), getXRot());
            }
            announce(nearbyPlayers(world), "The stage waits. Speak to the mask for an encore.");
            return;
        }
        super.die(source);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level().isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer) || !rehearsalArena || !defeatedOnce
                || active || tickCount < rematchReadyAge || distanceToSqr(player) > 36.0
                || player.isSpectator()) return InteractionResult.PASS;
        if (level().getDifficulty() == Difficulty.PEACEFUL) {
            serverPlayer.sendSystemMessage(Component.literal("The stage is still in peaceful worlds.")
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.SUCCESS_SERVER;
        }
        start((ServerLevel) level(), nearbyPlayers((ServerLevel) level()));
        return InteractionResult.SUCCESS_SERVER;
    }

    private void start(ServerLevel world, List<ServerPlayer> nearby) {
        if (arenaCenter == null) arenaCenter = blockPosition();
        currentStage = arenaCenter;
        findStages(world);
        active = true;
        setHealth(getMaxHealth());
        encore = false;
        stageIndex = 0;
        cycleTick = 0;
        pattern.clear();
        presenceTicks.clear();
        damagers.clear();
        moveStage(world);
        selectTarget(nearby);
        syncPhase();
        announce(nearby, "The Unbroken Punchline takes the stage. Leave the spotlight before it falls.");
    }

    private void stop() {
        active = false;
        currentStage = null;
        encore = false;
        cycleTick = 0;
        targetId = null;
        spot = null;
        counter = PunchlinePattern.Counter.NONE;
        pattern.clear();
        presenceTicks.clear();
        damagers.clear();
        syncPhase();
    }

    private void findStages(ServerLevel world) {
        // Three target blocks replace the center boards at Y offsets -1, +1, +3.
        // Search once per fight so template rotation needs no world-coordinate table.
        for (int i = 0; i < stageMarks.length; i++) stageMarks[i] = null;
        for (int i = 0; i < stageMarks.length; i++) {
            int y = arenaCenter.getY() - 1 + i * 2;
            for (int dx = -20; dx <= 20; dx++) {
                for (int dz = -20; dz <= 20; dz++) {
                    BlockPos mark = new BlockPos(arenaCenter.getX() + dx, y, arenaCenter.getZ() + dz);
                    if (world.getBlockState(mark).is(Blocks.TARGET)) {
                        stageMarks[i] = mark.above();
                        break;
                    }
                }
                if (stageMarks[i] != null) break;
            }
        }
    }

    private void moveStage(ServerLevel world) {
        BlockPos destination = stageMarks[stageIndex];
        if (destination == null || !world.getBlockState(destination).isAir()
                || !world.getBlockState(destination.above()).isAir()) return;
        world.sendParticles(SPOTLIGHT, getX(), getY() + 1, getZ(), 16, 0.5, 0.7, 0.5, 0.01);
        snapTo(destination.getX() + 0.5, destination.getY(),
                destination.getZ() + 0.5, getYRot(), getXRot());
        currentStage = destination;
        setDeltaMovement(Vec3.ZERO);
        world.sendParticles(LOCKED, getX(), getY() + 1, getZ(), 16, 0.5, 0.7, 0.5, 0.01);
    }

    private void keepOnStage(ServerLevel world) {
        BlockPos stage = currentStage;
        if (stage == null || !world.getBlockState(stage).isAir()
                || !world.getBlockState(stage.above()).isAir()) return;
        Vec3 center = Vec3.atBottomCenterOf(stage);
        if (position().distanceToSqr(center) > 0.01) {
            snapTo(center.x, center.y, center.z, getYRot(), getXRot());
        }
        setDeltaMovement(Vec3.ZERO);
    }

    private void selectTarget(List<ServerPlayer> candidates) {
        if (candidates.isEmpty()) {
            targetId = null;
            spot = null;
            counter = PunchlinePattern.Counter.NONE;
            return;
        }
        ServerPlayer selected = candidates.stream()
                .filter(player -> !player.getUUID().equals(previousTargetId))
                .min(Comparator.comparingDouble(player -> player.distanceToSqr(this)))
                .orElseGet(() -> candidates.stream()
                        .min(Comparator.comparingDouble(player -> player.distanceToSqr(this)))
                        .orElseThrow());
        targetId = selected.getUUID();
        previousTargetId = targetId;
        spot = selected.position();
        counter = pattern.counter(targetId);
    }

    private ServerPlayer target(ServerLevel world) {
        if (targetId == null) return null;
        ServerPlayer player = world.getServer().getPlayerList().getPlayer(targetId);
        return player != null && player.level() == world && player.isAlive()
                && !player.isSpectator() && player.distanceToSqr(this) <= ARENA_RADIUS * ARENA_RADIUS
                ? player : null;
    }

    private double spotRadius() {
        return counter == PunchlinePattern.Counter.PROJECTILE ? 3.5 : 2.2;
    }

    private void drawCircle(ServerLevel world, Vec3 center, double radius, DustParticleOptions dust) {
        for (int i = 0; i < 20; i++) {
            double theta = 2 * Math.PI * i / 20;
            world.sendParticles(dust, center.x + Math.cos(theta) * radius,
                    center.y + 0.15, center.z + Math.sin(theta) * radius,
                    1, 0.02, 0.02, 0.02, 0);
        }
    }

    private void strike(ServerLevel world) {
        if (spot == null) return;
        drawCircle(world, spot, spotRadius(), LOCKED);
        double radiusSquared = spotRadius() * spotRadius();
        for (ServerPlayer player : nearbyPlayers(world)) {
            double dx = player.getX() - spot.x;
            double dz = player.getZ() - spot.z;
            boolean underLight = Math.abs(player.getY() - spot.y) <= 2.0
                    && dx * dx + dz * dz <= radiusSquared;
            double bx = player.getX() - getX();
            double bz = player.getZ() - getZ();
            boolean atStageEdge = counter == PunchlinePattern.Counter.CLOSE
                    && Math.abs(player.getY() - getY()) <= 2.0
                    && bx * bx + bz * bz <= 3.5 * 3.5;
            if (underLight || atStageEdge) {
                player.hurtServer(world, world.damageSources().mobAttack(this), encore ? 9.0F : 7.0F);
            }
        }
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

    private void syncPhase() {
        getEntityData().set(PHASE, !active ? 0 : encore ? 2 : 1);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput view) {
        super.readAdditionalSaveData(view);
        rehearsalArena = view.getBooleanOr("RehearsalArena", false);
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
        view.putBoolean("RehearsalArena", rehearsalArena);
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
