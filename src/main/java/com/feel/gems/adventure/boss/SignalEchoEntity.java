package com.feel.gems.adventure.boss;

import com.feel.gems.particle.ModParticles;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.Level;

/** A short-lived scout that follows its caller and points out nearby threats. */
public final class SignalEchoEntity extends PathfinderMob {
    private static final int LIFE_TICKS = 30 * 20;
    private UUID ownerId;
    private int ticksLeft = LIFE_TICKS;
    private UUID lastThreat;

    public SignalEchoEntity(EntityType<? extends SignalEchoEntity> type, Level world) {
        super(type, world);
        setCustomName(Component.literal("Echo Scout"));
        setCustomNameVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.38)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void registerGoals() {
        // Its bounded follow and spotting behavior runs once per second below.
    }

    public void call(ServerPlayer owner) {
        ownerId = owner.getUUID();
        ticksLeft = LIFE_TICKS;
        setHealth(getMaxHealth());
    }

    public boolean belongsTo(ServerPlayer player) {
        return ownerId != null && ownerId.equals(player.getUUID());
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel world)) return;
        if (--ticksLeft <= 0 || ownerId == null) {
            discard();
            return;
        }
        if (tickCount % 20 != 0) return;
        ServerPlayer owner = world.getServer().getPlayerList().getPlayer(ownerId);
        if (owner == null || !owner.isAlive() || owner.level() != world) {
            discard();
            return;
        }
        double distance = distanceToSqr(owner);
        if (distance > 14 * 14) {
            BlockPos landing = owner.blockPosition().offset(1, 0, 1);
            if (world.getBlockState(landing).isAir()
                    && world.getBlockState(landing.above()).isAir()
                    && !world.getBlockState(landing.below())
                            .getCollisionShape(world, landing.below()).isEmpty()
                    && world.getFluidState(landing).isEmpty()) {
                snapTo(landing.getX() + 0.5, landing.getY(),
                        landing.getZ() + 0.5, getYRot(), getXRot());
                getNavigation().stop();
            }
        } else if (distance > 3 * 3) {
            getNavigation().moveTo(owner, 1.1);
        } else {
            getNavigation().stop();
        }
        if (tickCount % 40 == 0) spotThreat(world, owner);
    }

    private void spotThreat(ServerLevel world, ServerPlayer owner) {
        List<Monster> threats = world.getEntitiesOfClass(Monster.class,
                new AABB(this.blockPosition()).inflate(12),
                Monster::isAlive);
        Monster nearest = threats.stream()
                .min(Comparator.comparingDouble(owner::distanceToSqr)).orElse(null);
        if (nearest == null) {
            lastThreat = null;
            return;
        }
        world.sendParticles(ModParticles.GEM_STAR, nearest.getX(), nearest.getY() + 1.3,
                nearest.getZ(), 8, 0.35, 0.4, 0.35, 0.01);
        if (!nearest.getUUID().equals(lastThreat)) {
            owner.sendSystemMessage(Component.literal("Your echo spots a nearby threat."), true);
            lastThreat = nearest.getUUID();
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput view) {
        super.readAdditionalSaveData(view);
        String owner = view.getStringOr("Owner", "");
        try {
            ownerId = owner.isEmpty() ? null : UUID.fromString(owner);
        } catch (IllegalArgumentException ignored) {
            ownerId = null;
        }
        ticksLeft = Math.max(0, Math.min(LIFE_TICKS, view.getIntOr("TicksLeft", LIFE_TICKS)));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput view) {
        super.addAdditionalSaveData(view);
        if (ownerId != null) view.putString("Owner", ownerId.toString());
        view.putInt("TicksLeft", ticksLeft);
    }

    @Override
    public boolean isPersistenceRequired() {
        return true;
    }
}
