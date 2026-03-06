package com.feel.gems.entity;

import com.feel.gems.adventure.AdventureEntities;
import com.feel.gems.adventure.YehiaMothCue;
import com.feel.gems.adventure.YehiaStoryState;
import com.feel.gems.adventure.AdventureSounds;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/** A harmless folded-paper moth whose wings follow Yehia's shared garden tale. */
public final class StorybookFoldwingEntity extends PathfinderMob {
    private static final long SPAWN_RESERVATION_TICKS = 200;
    private static final Map<ServerLevel, Map<BlockPos, Long>> RECENT_TABLE_SPAWNS = new WeakHashMap<>();
    private static final EntityDataAccessor<Integer> CUE = SynchedEntityData.defineId(
            StorybookFoldwingEntity.class, EntityDataSerializers.INT);
    private int flutterCooldown;

    public StorybookFoldwingEntity(EntityType<? extends StorybookFoldwingEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 6.0)
                .add(Attributes.MOVEMENT_SPEED, 0.19);
    }

    /** Called only by Yehia's table interaction; one resident per loaded story room. */
    public static boolean ensureAtStoryTable(ServerLevel level, BlockPos table) {
        long now = level.getGameTime();
        Map<BlockPos, Long> reservations = RECENT_TABLE_SPAWNS.get(level);
        if (reservations != null) {
            reservations.entrySet().removeIf(entry -> now < entry.getValue()
                    || now - entry.getValue() >= SPAWN_RESERVATION_TICKS);
        }
        if (!level.getEntitiesOfClass(StorybookFoldwingEntity.class,
                new AABB(table).inflate(16), StorybookFoldwingEntity::isAlive).isEmpty()) {
            if (reservations != null) reservations.remove(table);
            return false;
        }
        if (reservations != null && reservations.containsKey(table)) return false;
        for (BlockPos pos : new BlockPos[]{table.east(2), table.west(2),
                table.north(2), table.south(2)}) {
            if (!level.getBlockState(pos.below()).isSolid()
                    || !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                    || !level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) {
                continue;
            }
            StorybookFoldwingEntity moth = AdventureEntities.STORYBOOK_FOLDWING.create(level, EntitySpawnReason.EVENT);
            if (moth == null) return false;
            moth.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            moth.setHomeTo(pos, 7);
            moth.setPersistenceRequired();
            if (level.addFreshEntity(moth)) {
                RECENT_TABLE_SPAWNS.computeIfAbsent(level, ignored -> new HashMap<>())
                        .put(table.immutable(), now);
                return true;
            }
            return false;
        }
        return false;
    }

    public static void clearTableReservations() {
        RECENT_TABLE_SPAWNS.clear();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CUE, YehiaMothCue.FOLDED);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new WaterAvoidingRandomStrollGoal(this, 0.8, 0.85F));
        goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 5.0F));
        goalSelector.addGoal(3, new RandomLookAroundGoal(this));
    }

    public int cuePhase() {
        return getEntityData().get(CUE);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        if (!hasHome()) setHomeTo(blockPosition(), 12);
        if (flutterCooldown > 0) flutterCooldown--;
        else if (onGround()) {
            getJumpControl().jump();
            flutterCooldown = 55 + getRandom().nextInt(45);
        }
        if (tickCount % 20 == 0) refreshCue(level);
    }

    private void refreshCue(ServerLevel level) {
        int next = YehiaMothCue.phase(YehiaStoryState.get(level.getServer()));
        if (next == cuePhase()) return;
        getEntityData().set(CUE, next);
        if (onGround()) getJumpControl().jump();
        showCue(level, next, 5);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || !isAlive() || player.isSpectator()) {
            return InteractionResult.PASS;
        }
        if (level().isClientSide()) return InteractionResult.SUCCESS;
        if (!(level() instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        YehiaStoryState story = YehiaStoryState.get(level.getServer());
        int phase = YehiaMothCue.phase(story);
        getEntityData().set(CUE, phase);
        serverPlayer.sendSystemMessage(Component.translatable(YehiaMothCue.messageKey(story)), true);
        showCue(level, phase, 12);
        if (onGround()) getJumpControl().jump();
        level.playSound(null, blockPosition(), AdventureSounds.STORY_PAGE_TURN,
                SoundSource.NEUTRAL, 0.45F, 1.55F);
        return InteractionResult.SUCCESS_SERVER;
    }

    private void showCue(ServerLevel level, int phase, int count) {
        level.sendParticles(new DustParticleOptions(YehiaMothCue.particleColor(phase), 0.75F),
                getX(), getY() + 0.55, getZ(), count, 0.23, 0.19, 0.23, 0.005);
    }
}
