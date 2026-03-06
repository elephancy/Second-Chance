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
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** A four-corridor fight: the committed prediction is vulnerable to a changed plan. */
public final class SecondMoveSolverEntity extends Monster {
    private static final int ARENA_RADIUS = 25;
    private static final int LOCK_TICK = 32;
    private static final int STRIKE_TICK = 55;
    private static final int NORMAL_RECOVERY = 42;
    private static final int BROKEN_RECOVERY = 72;
    private static final double LANE_WIDTH = 1.25;
    private static final DustParticleOptions PREVIEW = new DustParticleOptions(0x62CAE2, 1.15F);
    private static final DustParticleOptions LOCKED = new DustParticleOptions(0xD57282, 1.2F);

    private final SecondMovePlan plan = new SecondMovePlan();
    private final Map<UUID, Integer> presenceTicks = new HashMap<>();
    private final Set<UUID> damagers = new HashSet<>();
    private BlockPos arenaCenter;
    private UUID targetId;
    private UUID previousTargetId;
    private int cycleTick;
    private int rematchReadyAge;
    private boolean branchArena;
    private boolean active;
    private boolean defeatedOnce;
    private boolean brokenPrediction;

    public SecondMoveSolverEntity(EntityType<? extends SecondMoveSolverEntity> type, Level world) {
        super(type, world);
        setPersistenceRequired();
        setCustomName(Component.literal("The Second-Move Solver"));
        setCustomNameVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 165.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.FOLLOW_RANGE, 30.0)
                .add(Attributes.ARMOR, 5.0);
    }

    @Override
    protected void registerGoals() {
        // The four authored corridors, not chase AI, define the fight.
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel world) || !branchArena) return;
        if (arenaCenter == null) arenaCenter = blockPosition();
        // Melee knockback must not move the Solver off the junction used by
        // its route warnings, strike geometry, and restoration marks.
        if (active || !defeatedOnce) keepAtJunction(world);
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
        if (cycleTick < STRIKE_TICK && cycleTick % 3 == 0) {
            drawRoute(world, cycleTick < LOCK_TICK ? PREVIEW : LOCKED);
            drawCenter(world, LOCKED);
        }
        if (cycleTick == LOCK_TICK) {
            plan.lock();
            announce(nearbyPlayers(world), "The " + plan.predicted().label
                    + " route is locked. Take another way.");
            BossAudio.tell(nearbyPlayers(world), AdventureSounds.BRANCH_LOCK, 0.75F, 0.75F);
        }
        if (cycleTick == STRIKE_TICK) {
            drawRoute(world, LOCKED);
            strike(world);
            ServerPlayer target = target(world);
            brokenPrediction = target != null && plan.resolve(routeOf(target));
            if (brokenPrediction) {
                announce(nearbyPlayers(world), "You broke the plan. The Solver needs a moment.");
            } else {
                announce(nearbyPlayers(world), "The route is clear. Strike while it recalculates.");
            }
            BossAudio.tell(nearbyPlayers(world), AdventureSounds.BRANCH_STRIKE, 0.65F, 0.75F);
        }
        cycleTick++;
        if (cycleTick >= STRIKE_TICK + (brokenPrediction ? BROKEN_RECOVERY : NORMAL_RECOVERY)) {
            cycleTick = 0;
            brokenPrediction = false;
            selectTarget(nearbyPlayers(world));
        }
    }

    @Override
    public boolean hurtServer(ServerLevel world, DamageSource source, float amount) {
        if (!active || !branchArena) return false;
        Entity attacker = source.getEntity();
        if (attacker instanceof ServerPlayer player) {
            if (arenaCenter == null || player.distanceToSqr(Vec3.atCenterOf(arenaCenter))
                    > (ARENA_RADIUS + 4) * (ARENA_RADIUS + 4)) return false;
            damagers.add(player.getUUID());
        }
        float multiplier = cycleTick > STRIKE_TICK ? 1.0F : 0.35F;
        return super.hurtServer(world, source, Math.min(amount, 16.0F) * multiplier);
    }

    @Override
    public void die(DamageSource source) {
        if (branchArena && level() instanceof ServerLevel world) {
            Set<UUID> eligibleIds = BossParticipation.eligibleIds(presenceTicks, damagers);
            List<ServerPlayer> eligible = CampaignRewards.onlinePlayers(world.getServer(), eligibleIds);
            if (!eligibleIds.isEmpty()) {
                var victory = CampaignRewards.recordVictoryForIds(world.getServer(), eligibleIds,
                        LandmarkKey.BRANCH);
                CampaignSignatures.deliver(eligible);
                if (BranchRestoration.reveal(world, arenaCenter) > 0) {
                    announce(eligible, "Four routes remain. None belongs to the Solver now.");
                }
                CampaignRewards.deliverRematchSupplies(eligible, LandmarkKey.BRANCH, victory);
            }
            defeatedOnce = true;
            stop();
            rematchReadyAge = tickCount + 100;
            setHealth(getMaxHealth());
            if (arenaCenter != null) {
                snapTo(arenaCenter.getX() + 0.5, arenaCenter.getY(),
                        arenaCenter.getZ() + 0.5, getYRot(), getXRot());
            }
            announce(nearbyPlayers(world), "The junction is quiet. Speak to the Solver for another route.");
            return;
        }
        super.die(source);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level().isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer) || !branchArena || !defeatedOnce
                || active || tickCount < rematchReadyAge || distanceToSqr(player) > 36.0
                || player.isSpectator()) return InteractionResult.PASS;
        if (level().getDifficulty() == Difficulty.PEACEFUL) {
            serverPlayer.sendSystemMessage(Component.literal("The routes are still in peaceful worlds.")
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
        brokenPrediction = false;
        plan.clear();
        presenceTicks.clear();
        damagers.clear();
        selectTarget(nearby);
        announce(nearby, "The Solver chose a route. Make a different move before red locks it.");
    }

    private void stop() {
        active = false;
        cycleTick = 0;
        brokenPrediction = false;
        targetId = null;
        plan.clear();
        presenceTicks.clear();
        damagers.clear();
    }

    private void selectTarget(List<ServerPlayer> candidates) {
        if (candidates.isEmpty()) {
            targetId = null;
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
        SecondMovePlan.Route predicted = plan.begin(targetId, routeOf(selected));
        announce(candidates, "The " + predicted.label + " aisle is marked. The Solver cannot change it yet.");
    }

    private ServerPlayer target(ServerLevel world) {
        if (targetId == null) return null;
        ServerPlayer player = world.getServer().getPlayerList().getPlayer(targetId);
        return player != null && player.level() == world && player.isAlive()
                && !player.isSpectator() && player.distanceToSqr(this) <= ARENA_RADIUS * ARENA_RADIUS
                ? player : null;
    }

    private SecondMovePlan.Route routeOf(ServerPlayer player) {
        return SecondMovePlan.Route.of(player.getX() - arenaCenter.getX() - 0.5,
                player.getZ() - arenaCenter.getZ() - 0.5);
    }

    private void drawRoute(ServerLevel world, DustParticleOptions dust) {
        if (arenaCenter == null) return;
        SecondMovePlan.Route route = plan.predicted();
        for (int step = 3; step <= 10; step++) {
            double x = arenaCenter.getX() + 0.5 + route.dx * step;
            double z = arenaCenter.getZ() + 0.5 + route.dz * step;
            world.sendParticles(dust, x, arenaCenter.getY() + 0.25, z,
                    1, 0.06, 0.03, 0.06, 0);
        }
    }

    private void drawCenter(ServerLevel world, DustParticleOptions dust) {
        if (arenaCenter == null) return;
        for (int i = 0; i < 12; i++) {
            double theta = 2 * Math.PI * i / 12;
            world.sendParticles(dust, arenaCenter.getX() + 0.5 + Math.cos(theta) * 2.8,
                    arenaCenter.getY() + 0.25,
                    arenaCenter.getZ() + 0.5 + Math.sin(theta) * 2.8,
                    1, 0.03, 0.02, 0.03, 0);
        }
    }

    private void keepAtJunction(ServerLevel world) {
        if (arenaCenter == null || !world.getBlockState(arenaCenter).isAir()
                || !world.getBlockState(arenaCenter.above()).isAir()) return;
        Vec3 center = Vec3.atBottomCenterOf(arenaCenter);
        if (position().distanceToSqr(center) > 0.01) {
            snapTo(center.x, center.y, center.z, getYRot(), getXRot());
        }
        setDeltaMovement(Vec3.ZERO);
    }

    private void strike(ServerLevel world) {
        SecondMovePlan.Route route = plan.predicted();
        for (ServerPlayer player : nearbyPlayers(world)) {
            if (Math.abs(player.getY() - arenaCenter.getY()) > 2.0) continue;
            double dx = player.getX() - arenaCenter.getX() - 0.5;
            double dz = player.getZ() - arenaCenter.getZ() - 0.5;
            double along = dx * route.dx + dz * route.dz;
            double across = dx * route.dz - dz * route.dx;
            boolean lane = along >= 3 && along <= 10.5 && Math.abs(across) <= LANE_WIDTH;
            boolean center = dx * dx + dz * dz <= 2.8 * 2.8;
            if (lane || center) {
                player.hurtServer(world, world.damageSources().mobAttack(this), 8.0F);
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
            player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.AQUA), true);
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput view) {
        super.readAdditionalSaveData(view);
        branchArena = view.getBooleanOr("BranchArena", false);
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
        view.putBoolean("BranchArena", branchArena);
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
