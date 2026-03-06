package com.feel.gems.adventure.boss;

import com.feel.gems.adventure.CampaignVisits;
import com.feel.gems.adventure.LandmarkKey;

import com.feel.gems.adventure.AdventureSounds;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.Level;

/** A small gallery sentry that teaches the boss's locked-line cover rule. */
public final class SketchguardEntity extends Monster {
    private static final int RANGE = 12;
    private static final int LOCK_TICK = 20;
    private static final int FIRE_TICK = 36;
    private static final int RESET_TICK = 60;
    private static final DustParticleOptions AIM_INK = new DustParticleOptions(0x72B9C5, 0.8F);
    private static final DustParticleOptions LOCK_INK = new DustParticleOptions(0xE8AB74, 0.95F);
    private BlockPos home;
    private UUID targetId;
    private Vec3 lockedPoint;
    private int cycleTick;

    public SketchguardEntity(EntityType<? extends SketchguardEntity> type, Level world) {
        super(type, world);
        setPersistenceRequired();
        setCustomName(Component.literal("Sketchguard"));
        setCustomNameVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 24.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.FOLLOW_RANGE, RANGE);
    }

    @Override
    protected void registerGoals() {
        // The sentry holds its authored cover-training position.
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel world)
                || world.getDifficulty() == Difficulty.PEACEFUL) return;
        if (home == null) home = blockPosition();
        if (cycleTick == 0) {
            List<ServerPlayer> players = nearby(world);
            if (players.isEmpty()) return;
            CampaignVisits.markNearby(world, players, LandmarkKey.FRAMEFALL);
            ServerPlayer chosen = players.stream()
                    .min(Comparator.comparingDouble(player -> player.distanceToSqr(this)))
                    .orElseThrow();
            targetId = chosen.getUUID();
            lockedPoint = chosen.getEyePosition();
            BossAudio.tell(players, AdventureSounds.VERDICT_AIM, 0.6F, 1.2F);
        }
        ServerPlayer target = targetId == null ? null
                : world.getServer().getPlayerList().getPlayer(targetId);
        if (cycleTick < LOCK_TICK && target != null && target.isAlive()
                && target.level() == world && target.distanceToSqr(this) <= RANGE * RANGE) {
            lockedPoint = target.getEyePosition();
        }
        if (lockedPoint != null && cycleTick < FIRE_TICK && cycleTick % 4 == 0) {
            InkLane.draw(world, this, getEyePosition(), lockedPoint,
                    cycleTick < LOCK_TICK ? AIM_INK : LOCK_INK, 12);
        }
        if (cycleTick == FIRE_TICK && lockedPoint != null) {
            BossAudio.tell(nearby(world), AdventureSounds.VERDICT_RELEASE, 0.7F, 1.2F);
            Vec3 from = getEyePosition();
            InkLane.draw(world, this, from, lockedPoint, LOCK_INK, 12);
            for (ServerPlayer player : nearby(world)) {
                if (InkLane.exposed(world, this, from, lockedPoint,
                        player.getEyePosition(), 0.85 * 0.85)) {
                    player.hurtServer(world, world.damageSources().mobAttack(this), 3.0F);
                }
            }
        }
        cycleTick = cycleTick >= RESET_TICK ? 0 : cycleTick + 1;
    }

    private List<ServerPlayer> nearby(ServerLevel world) {
        return world.getEntitiesOfClass(ServerPlayer.class, new AABB(home).inflate(RANGE),
                player -> player.isAlive() && !player.isSpectator()
                        && player.distanceToSqr(Vec3.atCenterOf(home)) <= RANGE * RANGE);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput view) {
        super.readAdditionalSaveData(view);
        int x = view.getIntOr("HomeX", Integer.MIN_VALUE);
        int y = view.getIntOr("HomeY", Integer.MIN_VALUE);
        int z = view.getIntOr("HomeZ", Integer.MIN_VALUE);
        home = x == Integer.MIN_VALUE || y == Integer.MIN_VALUE || z == Integer.MIN_VALUE
                ? null : new BlockPos(x, y, z);
        // A loaded sentry restarts with a full warning, never an instant shot.
        cycleTick = 0;
        targetId = null;
        lockedPoint = null;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput view) {
        super.addAdditionalSaveData(view);
        if (home != null) {
            view.putInt("HomeX", home.getX());
            view.putInt("HomeY", home.getY());
            view.putInt("HomeZ", home.getZ());
        }
    }

    @Override
    public boolean isPersistenceRequired() { return true; }
}
