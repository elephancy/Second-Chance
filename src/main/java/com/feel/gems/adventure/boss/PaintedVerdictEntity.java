package com.feel.gems.adventure.boss;

import com.feel.gems.adventure.CampaignRewards;
import com.feel.gems.adventure.CampaignPersistentState;
import com.feel.gems.friend.FriendDialoguePayload;
import com.feel.gems.friend.FriendRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.Level;

/**
 * A gallery fight built around visible firing lanes and real block cover.
 * All targeting, damage and campaign credit run on the server. The gallery
 * template places this entity dormant with FramefallArena=1b.
 */
public final class PaintedVerdictEntity extends Monster {
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(
            PaintedVerdictEntity.class, EntityDataSerializers.INT);
    private static final int ARENA_RADIUS = 24;
    private static final double LANE_WIDTH_SQUARED = 1.35 * 1.35;
    private static final DustParticleOptions AIM_INK = new DustParticleOptions(0x3CB9C2, 1.25F);
    private static final DustParticleOptions LOCK_INK = new DustParticleOptions(0xED6A3C, 1.35F);
    private final VerdictSequence sequence = new VerdictSequence();
    private final Map<UUID, Integer> presenceTicks = new HashMap<>();
    private final Set<UUID> damagers = new HashSet<>();
    private BlockPos arenaCenter;
    private UUID targetId;
    private UUID previousTargetId;
    private Vec3 lockedPoint;
    private int frameIndex;
    private boolean framefallArena;
    private boolean defeatedOnce;
    private int rematchReadyAge;

    public PaintedVerdictEntity(EntityType<? extends PaintedVerdictEntity> type, Level world) {
        super(type, world);
        setPersistenceRequired();
        setCustomName(Component.literal("The Painted Verdict"));
        setCustomNameVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 120.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.FOLLOW_RANGE, 28.0)
                .add(Attributes.ARMOR, 5.0);
    }

    @Override
    protected void registerGoals() {
        // The encounter has an authored sequence rather than ordinary chase AI.
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(PHASE, VerdictSequence.Phase.DORMANT.ordinal());
    }

    public VerdictSequence.Phase phase() {
        int index = getEntityData().get(PHASE);
        VerdictSequence.Phase[] values = VerdictSequence.Phase.values();
        return values[Math.clamp(index, 0, values.length - 1)];
    }

    /** Only the authored Framefall structure should arm the campaign encounter. */
    public void armForFramefall() {
        framefallArena = true;
        arenaCenter = blockPosition();
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel world) || !framefallArena) return;
        if (arenaCenter == null) arenaCenter = blockPosition();
        if (world.getDifficulty() == Difficulty.PEACEFUL) {
            sequence.reset();
            presenceTicks.clear();
            damagers.clear();
            setHealth(getMaxHealth());
            syncPhase();
            return;
        }
        if (tickCount % 20 == 0) {
            List<ServerPlayer> nearby = nearbyPlayers(world, ARENA_RADIUS);
            if (sequence.phase() == VerdictSequence.Phase.DORMANT) {
                // Entering the court starts the fight; merely loading the structure does not.
                if (!defeatedOnce && nearby.stream().anyMatch(player -> player.distanceToSqr(
                        Vec3.atCenterOf(arenaCenter)) < 12 * 12)) {
                    sequence.start();
                    playCue(world, AdventureSounds.VERDICT_AIM, 0.85F, 0.9F);
                    announce(nearby, "The frame closes. Watch the painted line.");
                    selectTarget(nearby);
                    syncPhase();
                }
            } else {
                for (ServerPlayer player : nearby) {
                    presenceTicks.merge(player.getUUID(), 20, Integer::sum);
                }
                if (nearby.isEmpty()) {
                    sequence.reset();
                    presenceTicks.clear();
                    damagers.clear();
                    setHealth(getMaxHealth());
                    targetId = null;
                    lockedPoint = null;
                    syncPhase();
                    return;
                }
            }
        }
        if (sequence.phase() == VerdictSequence.Phase.DORMANT) return;
        ServerPlayer target = resolveTarget(world);
        if (sequence.phase() == VerdictSequence.Phase.TELL) {
            if (target == null && tickCount % 20 == 0) {
                selectTarget(nearbyPlayers(world, ARENA_RADIUS));
                target = resolveTarget(world);
            }
            if (target != null && !sequence.aimLocked()) {
                lockedPoint = target.getEyePosition();
            }
            if (lockedPoint != null && tickCount % 3 == 0) {
                drawLane(world, sequence.aimLocked() ? LOCK_INK : AIM_INK);
            }
            if (sequence.ticks() == VerdictSequence.LOCK_TICK) {
                announce(nearbyPlayers(world, ARENA_RADIUS), "Line locked. Break sight or step clear.");
                playCue(world, AdventureSounds.VERDICT_AIM, 0.7F, 1.2F);
            }
        }
        VerdictSequence.Phase previous = sequence.phase();
        if (!sequence.tick()) return;
        syncPhase();
        if (previous == VerdictSequence.Phase.TELL) {
            playCue(world, AdventureSounds.VERDICT_RELEASE, 1.0F, 0.92F);
            fire(world);
        } else if (previous == VerdictSequence.Phase.FIRE) {
            announce(nearbyPlayers(world, ARENA_RADIUS), "The paint dries. Strike now.");
        } else if (previous == VerdictSequence.Phase.RECOVERY) {
            moveFrame(world);
            selectTarget(nearbyPlayers(world, ARENA_RADIUS));
            playCue(world, AdventureSounds.VERDICT_AIM, 0.85F, 0.9F);
            announce(nearbyPlayers(world, ARENA_RADIUS), "A new angle. Find cover again.");
        }
    }

    @Override
    public boolean hurtServer(ServerLevel world, DamageSource source, float amount) {
        if (sequence.phase() == VerdictSequence.Phase.DORMANT || !framefallArena) return false;
        Entity attacker = source.getEntity();
        if (attacker instanceof ServerPlayer player) {
            // A distant shooter cannot skip the gallery's cover and lane rules.
            if (arenaCenter == null || player.distanceToSqr(Vec3.atCenterOf(arenaCenter))
                    > (ARENA_RADIUS + 4) * (ARENA_RADIUS + 4)) return false;
            damagers.add(player.getUUID());
        }
        // Recovery is the intended damage window; damage is capped per hit.
        float multiplier = sequence.phase() == VerdictSequence.Phase.RECOVERY ? 1.0F : 0.35F;
        return super.hurtServer(world, source, Math.min(amount, 16.0F) * multiplier);
    }

    @Override
    public void die(DamageSource source) {
        if (framefallArena && level() instanceof ServerLevel world) {
            Set<UUID> eligibleIds = BossParticipation.eligibleIds(presenceTicks, damagers);
            List<ServerPlayer> eligible = CampaignRewards.onlinePlayers(world.getServer(), eligibleIds);
            if (!eligibleIds.isEmpty()) {
                var victory = CampaignRewards.recordVictoryForIds(world.getServer(), eligibleIds, LandmarkKey.FRAMEFALL);
                if (arenaCenter != null
                        && FramefallRestoration.revealPanels(world, arenaCenter) > 0) {
                    announce(eligible, "The gallery's painted frames turn violet.");
                }
                CampaignSignatures.deliver(eligible);
                CampaignRewards.deliverRematchSupplies(eligible, LandmarkKey.FRAMEFALL, victory);
            }
            // Keep the authored encounter in place. Replays require an explicit
            // interaction so a victory never starts another fight immediately.
            defeatedOnce = true;
            sequence.reset();
            syncPhase();
            targetId = null;
            lockedPoint = null;
            presenceTicks.clear();
            damagers.clear();
            rematchReadyAge = tickCount + 100;
            setHealth(getMaxHealth());
            if (arenaCenter != null) {
                snapTo(arenaCenter.getX() + 0.5, arenaCenter.getY(),
                        arenaCenter.getZ() + 0.5, getYRot(), getXRot());
            }
            announce(nearbyPlayers(world, ARENA_RADIUS),
                    "The Verdict yields. Speak to the frame for another exchange.");
            return;
        }
        super.die(source);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level().isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer)
                || !framefallArena || !defeatedOnce
                || distanceToSqr(player) > 36.0 || player.isSpectator()) {
            return InteractionResult.PASS;
        }
        if (sequence.phase() != VerdictSequence.Phase.DORMANT) return InteractionResult.PASS;
        boolean personalClear = CampaignPersistentState.get(serverPlayer.level().getServer())
                .model().hasCleared(serverPlayer.getUUID(), LandmarkKey.FRAMEFALL);
        if (personalClear && !player.isShiftKeyDown()) {
            FriendRegistry.find("mohanad").ifPresent(definition ->
                    ServerPlayNetworking.send(serverPlayer, new FriendDialoguePayload(
                            definition.id(), definition.name(), definition.hint(), definition.lines())));
            return InteractionResult.SUCCESS_SERVER;
        }
        if (tickCount < rematchReadyAge) return InteractionResult.SUCCESS_SERVER;
        if (level().getDifficulty() == Difficulty.PEACEFUL) {
            serverPlayer.sendSystemMessage(Component.literal("The frame is still in peaceful worlds.")
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.SUCCESS_SERVER;
        }
        sequence.start();
        playCue((ServerLevel) level(), AdventureSounds.VERDICT_AIM, 0.85F, 0.9F);
        selectTarget(nearbyPlayers((ServerLevel) level(), ARENA_RADIUS));
        syncPhase();
        serverPlayer.sendSystemMessage(Component.literal("The frame opens for another exchange.")
                .withStyle(ChatFormatting.AQUA), true);
        return InteractionResult.SUCCESS_SERVER;
    }

    private void fire(ServerLevel world) {
        if (lockedPoint == null) return;
        drawLane(world, LOCK_INK);
        Vec3 from = getEyePosition();
        for (ServerPlayer player : nearbyPlayers(world, ARENA_RADIUS)) {
            if (!InkLane.exposed(world, this, from, lockedPoint,
                    player.getEyePosition(), LANE_WIDTH_SQUARED)) continue;
            player.hurtServer(world, world.damageSources().mobAttack(this), 8.0F);
        }
    }

    private void drawLane(ServerLevel world, DustParticleOptions ink) {
        InkLane.draw(world, this, getEyePosition(), lockedPoint, ink, 24);
    }

    private void moveFrame(ServerLevel world) {
        if (arenaCenter == null) return;
        // Four authored positions around the central sculpture. No blocks are edited.
        int[][] offsets = {{-7, -7}, {7, -7}, {7, 7}, {-7, 7}};
        for (int tries = 0; tries < offsets.length; tries++) {
            frameIndex = (frameIndex + 1) % offsets.length;
            BlockPos destination = arenaCenter.offset(offsets[frameIndex][0], 0, offsets[frameIndex][1]);
            if (!world.getBlockState(destination).isAir()
                    || !world.getBlockState(destination.above()).isAir()
                    || !world.getBlockState(destination.below()).isSolid()) continue;
            world.sendParticles(AIM_INK, getX(), getY() + 1, getZ(), 18, 0.6, 0.8, 0.6, 0);
            snapTo(destination.getX() + 0.5, destination.getY(),
                    destination.getZ() + 0.5, getYRot(), getXRot());
            world.sendParticles(LOCK_INK, getX(), getY() + 1, getZ(), 18, 0.6, 0.8, 0.6, 0);
            return;
        }
    }

    private void selectTarget(List<ServerPlayer> candidates) {
        if (candidates.isEmpty()) {
            targetId = null;
            lockedPoint = null;
            return;
        }
        candidates.sort((a, b) -> Double.compare(a.distanceToSqr(this), b.distanceToSqr(this)));
        ServerPlayer selected = candidates.stream()
                .filter(player -> !player.getUUID().equals(previousTargetId))
                .findFirst().orElse(candidates.getFirst());
        targetId = selected.getUUID();
        previousTargetId = targetId;
        lockedPoint = selected.getEyePosition();
    }

    private ServerPlayer resolveTarget(ServerLevel world) {
        if (targetId == null) return null;
        ServerPlayer player = world.getServer().getPlayerList().getPlayer(targetId);
        return player != null && player.level() == world && !player.isSpectator()
                && player.isAlive() && player.distanceToSqr(this) <= ARENA_RADIUS * ARENA_RADIUS
                ? player : null;
    }

    private List<ServerPlayer> nearbyPlayers(ServerLevel world, int radius) {
        if (arenaCenter == null) return List.of();
        AABB area = new AABB(arenaCenter).inflate(radius);
        return new ArrayList<>(world.getEntitiesOfClass(ServerPlayer.class, area,
                player -> player.isAlive() && !player.isSpectator()));
    }

    private void announce(List<ServerPlayer> players, String message) {
        for (ServerPlayer player : players) {
            player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.AQUA), true);
        }
    }

    private void syncPhase() {
        getEntityData().set(PHASE, sequence.phase().ordinal());
    }

    private void playCue(ServerLevel world, SoundEvent sound, float volume, float pitch) {
        BossAudio.tell(nearbyPlayers(world, ARENA_RADIUS), sound, volume, pitch);
    }


    @Override
    protected void readAdditionalSaveData(ValueInput view) {
        super.readAdditionalSaveData(view);
        framefallArena = view.getBooleanOr("FramefallArena", false);
        defeatedOnce = view.getBooleanOr("DefeatedOnce", false);
        int x = view.getIntOr("ArenaX", Integer.MIN_VALUE);
        int y = view.getIntOr("ArenaY", Integer.MIN_VALUE);
        int z = view.getIntOr("ArenaZ", Integer.MIN_VALUE);
        // Entity Pos may not have been restored when custom NBT is read.
        // The first server tick captures the placed position if no anchor was saved.
        arenaCenter = x == Integer.MIN_VALUE || y == Integer.MIN_VALUE || z == Integer.MIN_VALUE
                ? null : new BlockPos(x, y, z);
        // Restart a loaded fight with a full, readable tell, never a surprise shot.
        sequence.reset();
        setHealth(getMaxHealth());
        syncPhase();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput view) {
        super.addAdditionalSaveData(view);
        view.putBoolean("FramefallArena", framefallArena);
        view.putBoolean("DefeatedOnce", defeatedOnce);
        if (arenaCenter != null) {
            view.putInt("ArenaX", arenaCenter.getX());
            view.putInt("ArenaY", arenaCenter.getY());
            view.putInt("ArenaZ", arenaCenter.getZ());
        }
    }

    @Override
    public boolean isPersistenceRequired() { return true; }
}
