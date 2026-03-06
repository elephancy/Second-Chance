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

/** Three false calls, one steady warning, then one honest signal pulse. */
public final class EmptyEncoreEntity extends Monster {
    private static final int ARENA_RADIUS = 27;
    private static final int REVEAL_TICK = 34;
    private static final int STRIKE_TICK = 66;
    private static final int CYCLE_TICKS = 122;
    private static final DustParticleOptions FALSE = new DustParticleOptions(0xD9A56D, 1.1F);
    private static final DustParticleOptions TRUE = new DustParticleOptions(0x80DDE5, 1.2F);

    private final EncorePattern pattern = new EncorePattern();
    private final Map<UUID, Integer> presenceTicks = new HashMap<>();
    private final Map<UUID, Integer> warningAgainAt = new HashMap<>();
    private final Set<UUID> contributors = new HashSet<>();
    private BlockPos arenaCenter;
    private int cycleTick;
    private int rematchReadyAge;
    private int receiverRotation = -1;
    private boolean hushwireArena;
    private boolean active;
    private boolean defeatedOnce;

    public EmptyEncoreEntity(EntityType<? extends EmptyEncoreEntity> type, Level world) {
        super(type, world);
        setPersistenceRequired();
        setCustomName(Component.literal("The Empty Encore"));
        setCustomNameVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 185.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.FOLLOW_RANGE, 30.0)
                .add(Attributes.ARMOR, 5.0);
    }

    @Override
    protected void registerGoals() {
        // The record hall's receivers define the encounter.
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel world) || !hushwireArena) return;
        if (arenaCenter == null) arenaCenter = blockPosition();
        keepOnStage(world);
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
            drawReceivers(world);
        }
        if (cycleTick == 10) {
            BossAudio.tell(nearbyPlayers(world), AdventureSounds.HUSHWIRE_FALSE, 0.6F, 0.85F);
        }
        if (cycleTick == REVEAL_TICK) {
            announce(nearbyPlayers(world), "Only the steady blue signal will pulse. Move off its line.");
            BossAudio.tell(nearbyPlayers(world), AdventureSounds.HUSHWIRE_TRUE, 0.8F, 1.0F);
        }
        if (cycleTick == STRIKE_TICK) {
            drawReceivers(world);
            strike(world);
            announce(nearbyPlayers(world), "The signal passed. Now answer it.");
            BossAudio.tell(nearbyPlayers(world), AdventureSounds.HUSHWIRE_PULSE, 0.8F, 0.9F);
        }
        cycleTick++;
        if (cycleTick >= CYCLE_TICKS) {
            cycleTick = 0;
            chooseSignal(world);
        }
    }

    @Override
    public boolean hurtServer(ServerLevel world, DamageSource source, float amount) {
        if (!active || !hushwireArena) return false;
        Entity attacker = source.getEntity();
        if (attacker instanceof ServerPlayer player) {
            if (arenaCenter == null || player.distanceToSqr(Vec3.atCenterOf(arenaCenter))
                    > (ARENA_RADIUS + 4) * (ARENA_RADIUS + 4)) return false;
            contributors.add(player.getUUID());
            if (cycleTick < STRIKE_TICK && tickCount >= warningAgainAt.getOrDefault(player.getUUID(), 0)) {
                warningAgainAt.put(player.getUUID(), tickCount + 35);
                player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 25, 0));
                player.sendSystemMessage(Component.literal("That was a false call. Wait for the steady blue signal.")
                        .withStyle(ChatFormatting.GRAY), true);
            }
        }
        float multiplier = cycleTick > STRIKE_TICK ? 1.0F : 0.2F;
        return super.hurtServer(world, source, Math.min(amount, 16.0F) * multiplier);
    }

    @Override
    public void die(DamageSource source) {
        if (hushwireArena && level() instanceof ServerLevel world) {
            Set<UUID> eligibleIds = BossParticipation.eligibleIds(presenceTicks, contributors);
            List<ServerPlayer> eligible = CampaignRewards.onlinePlayers(world.getServer(), eligibleIds);
            if (!eligibleIds.isEmpty()) {
                var victory = CampaignRewards.recordVictoryForIds(world.getServer(), eligibleIds,
                        LandmarkKey.HUSHWIRE);
                CampaignSignatures.deliver(eligible);
                if (EncoreRestoration.reveal(world, arenaCenter, receiverRotation(world)) > 0) {
                    announce(eligible, "The receivers hold a clear signal now.");
                }
                CampaignRewards.deliverRematchSupplies(eligible, LandmarkKey.HUSHWIRE, victory);
            }
            defeatedOnce = true;
            stop();
            rematchReadyAge = tickCount + 100;
            setHealth(getMaxHealth());
            keepOnStage(world);
            announce(nearbyPlayers(world), "The hall is quiet. Speak to the signal for a rematch.");
            return;
        }
        super.die(source);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level().isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer) || !hushwireArena || !defeatedOnce
                || active || tickCount < rematchReadyAge || distanceToSqr(player) > 36.0
                || player.isSpectator()) return InteractionResult.PASS;
        if (level().getDifficulty() == Difficulty.PEACEFUL) {
            serverPlayer.sendSystemMessage(Component.literal("The frequency is quiet in peaceful worlds.")
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.SUCCESS_SERVER;
        }
        start(nearbyPlayers((ServerLevel) level()));
        return InteractionResult.SUCCESS_SERVER;
    }

    public boolean showTruth(ServerPlayer player) {
        if (!active || arenaCenter == null || pattern.real() == null
                || player.level() != level()
                || player.distanceToSqr(this) > ARENA_RADIUS * ARENA_RADIUS) return false;
        EncorePattern.Receiver real = pattern.real();
        BlockPos pad = receiverPad(real);
        ((ServerLevel) level()).sendParticles(TRUE, pad.getX() + 0.5,
                pad.getY() + 0.4, pad.getZ() + 0.5, 20, 0.35, 0.25, 0.35, 0);
        player.sendSystemMessage(Component.literal("The " + real.label + " receiver is the real call.")
                .withStyle(ChatFormatting.AQUA), true);
        return true;
    }

    private void start(List<ServerPlayer> nearby) {
        if (arenaCenter == null) arenaCenter = blockPosition();
        keepOnStage((ServerLevel) level());
        receiverRotation((ServerLevel) level());
        setHealth(getMaxHealth());
        active = true;
        cycleTick = 0;
        pattern.reset();
        presenceTicks.clear();
        warningAgainAt.clear();
        contributors.clear();
        chooseSignal((ServerLevel) level());
        announce(nearby, "Three signals call. Wait for the one that holds steady.");
    }

    private void stop() {
        active = false;
        cycleTick = 0;
        pattern.reset();
        presenceTicks.clear();
        warningAgainAt.clear();
        contributors.clear();
    }

    private void keepOnStage(ServerLevel world) {
        if (arenaCenter == null || !world.getBlockState(arenaCenter).isAir()
                || !world.getBlockState(arenaCenter.above()).isAir()) return;
        Vec3 center = Vec3.atBottomCenterOf(arenaCenter);
        if (position().distanceToSqr(center) > 0.01) {
            snapTo(center.x, center.y, center.z, getYRot(), getXRot());
        }
        setDeltaMovement(Vec3.ZERO);
    }

    private void chooseSignal(ServerLevel world) {
        pattern.next(random.nextInt(3));
    }

    private int receiverRotation(ServerLevel world) {
        if (receiverRotation >= 0) return receiverRotation;
        int bestRotation = 0;
        int bestMatches = -1;
        for (int rotation = 0; rotation < 4; rotation++) {
            int matches = 0;
            for (EncorePattern.Receiver receiver : EncorePattern.Receiver.values()) {
                BlockPos pad = EncorePattern.pad(arenaCenter, receiver, rotation).below();
                if (world.getBlockState(pad).is(net.minecraft.world.level.block.Blocks.CONCRETE.blue())
                        || world.getBlockState(pad).is(net.minecraft.world.level.block.Blocks.GOLD_BLOCK)) {
                    matches++;
                }
            }
            if (matches > bestMatches) {
                bestMatches = matches;
                bestRotation = rotation;
            }
        }
        receiverRotation = bestRotation;
        return receiverRotation;
    }

    private BlockPos receiverPad(EncorePattern.Receiver receiver) {
        return EncorePattern.pad(arenaCenter, receiver, receiverRotation((ServerLevel) level()));
    }

    private void drawReceivers(ServerLevel world) {
        for (EncorePattern.Receiver receiver : EncorePattern.Receiver.values()) {
            boolean real = pattern.isReal(receiver);
            DustParticleOptions color = cycleTick >= REVEAL_TICK && real ? TRUE : FALSE;
            int count = cycleTick >= REVEAL_TICK && !real ? 1 : 3;
            BlockPos pad = receiverPad(receiver);
            world.sendParticles(color,
                    pad.getX() + 0.5,
                    arenaCenter.getY() + 0.25,
                    pad.getZ() + 0.5,
                    count, 0.28, 0.04, 0.28, 0);
        }
        if (cycleTick >= REVEAL_TICK) drawTrueLine(world);
    }

    private void drawTrueLine(ServerLevel world) {
        EncorePattern.Receiver real = pattern.real();
        if (real == null) return;
        BlockPos pad = receiverPad(real);
        int dx = pad.getX() - arenaCenter.getX();
        int dz = pad.getZ() - arenaCenter.getZ();
        for (int step = 0; step <= 10; step++) {
            double fraction = step / 10.0;
            world.sendParticles(TRUE,
                    arenaCenter.getX() + 0.5 + dx * fraction,
                    arenaCenter.getY() + 0.3,
                    arenaCenter.getZ() + 0.5 + dz * fraction,
                    1, 0.05, 0.03, 0.05, 0);
        }
    }

    private void strike(ServerLevel world) {
        EncorePattern.Receiver real = pattern.real();
        if (real == null) return;
        BlockPos receiver = receiverPad(real);
        int dx = receiver.getX() - arenaCenter.getX();
        int dz = receiver.getZ() - arenaCenter.getZ();
        double centerX = arenaCenter.getX() + 0.5;
        double centerZ = arenaCenter.getZ() + 0.5;
        double padX = centerX + dx;
        double padZ = centerZ + dz;
        double lengthSq = dx * dx + dz * dz;
        for (ServerPlayer player : nearbyPlayers(world)) {
            if (Math.abs(player.getY() - arenaCenter.getY()) > 2.0) continue;
            double px = player.getX();
            double pz = player.getZ();
            double projection = ((px - centerX) * dx + (pz - centerZ) * dz) / lengthSq;
            double clamped = Math.max(0.0, Math.min(1.0, projection));
            double nearestX = centerX + dx * clamped;
            double nearestZ = centerZ + dz * clamped;
            boolean line = projection >= 0 && projection <= 1
                    && (px - nearestX) * (px - nearestX)
                    + (pz - nearestZ) * (pz - nearestZ) <= 1.2 * 1.2;
            boolean pad = (px - padX) * (px - padX) + (pz - padZ) * (pz - padZ) <= 2.8 * 2.8;
            if (line || pad) {
                player.hurtServer(world, world.damageSources().mobAttack(this), 9.0F);
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
        hushwireArena = view.getBooleanOr("HushwireArena", false);
        defeatedOnce = view.getBooleanOr("DefeatedOnce", false);
        int x = view.getIntOr("ArenaX", Integer.MIN_VALUE);
        int y = view.getIntOr("ArenaY", Integer.MIN_VALUE);
        int z = view.getIntOr("ArenaZ", Integer.MIN_VALUE);
        arenaCenter = x == Integer.MIN_VALUE || y == Integer.MIN_VALUE || z == Integer.MIN_VALUE
                ? null : new BlockPos(x, y, z);
        receiverRotation = -1;
        stop();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput view) {
        super.addAdditionalSaveData(view);
        view.putBoolean("HushwireArena", hushwireArena);
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
