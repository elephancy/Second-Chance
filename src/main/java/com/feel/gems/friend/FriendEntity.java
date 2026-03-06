package com.feel.gems.friend;

import com.feel.gems.adventure.CampaignPersistentState;
import com.feel.gems.adventure.CampaignRouteState;
import com.feel.gems.adventure.RouteFriendReactions;
import com.feel.gems.adventure.RouteFinaleState;

import com.feel.gems.casino.CasinoService;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** A neutral group member. Interactions use vanilla entity reach and run on the server. */
public final class FriendEntity extends PathfinderMob {
    private static final EntityDataAccessor<String> FRIEND_ID = SynchedEntityData.defineId(
            FriendEntity.class, EntityDataSerializers.STRING);
    private static final double MAX_TALK_DISTANCE_SQUARED = 36.0;
    private boolean profileInitialized;

    public FriendEntity(EntityType<? extends FriendEntity> type, Level world) {
        super(type, world);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.24);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(FRIEND_ID, "");
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(2, new RandomLookAroundGoal(this));
    }

    public String friendId() {
        return getEntityData().get(FRIEND_ID);
    }

    /** Assign a known roster identity before spawning this entity. */
    public boolean assign(String idOrAlias) {
        return FriendRegistry.find(idOrAlias).map(definition -> {
            getEntityData().set(FRIEND_ID, definition.id());
            applyName(definition);
            return true;
        }).orElse(false);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && !profileInitialized) {
            String savedId = friendId();
            FriendDefinition definition = (savedId.isEmpty()
                    ? FriendRegistry.randomTownFriend(getRandom())
                    : FriendRegistry.find(savedId)).orElse(null);
            if (definition == null && !savedId.isEmpty()) {
                discard();
                return;
            }
            if (definition != null) {
                getEntityData().set(FRIEND_ID, definition.id());
                applyName(definition);
                profileInitialized = true;
            }
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || !isAlive() || player.isSpectator()
                || distanceToSqr(player) > MAX_TALK_DISTANCE_SQUARED) {
            return InteractionResult.PASS;
        }
        if (level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        if ("ragab".equals(friendId()) && !player.isShiftKeyDown()) {
            CasinoService.open(serverPlayer, this);
            return InteractionResult.SUCCESS_SERVER;
        }
        FriendDefinition definition = FriendRegistry.find(friendId()).orElse(null);
        if (definition == null) return InteractionResult.PASS;
        if (definition.silent()) {
            serverPlayer.sendSystemMessage(Component.literal("..."), true);
            return InteractionResult.SUCCESS_SERVER;
        }
        var progress = CampaignPersistentState.get(serverPlayer.level().getServer())
                .model().progress(serverPlayer.getUUID());
        String routeReaction = RouteFriendReactions.line(
                CampaignRouteState.get(serverPlayer.level().getServer()).chosen(),
                definition.id(), RouteFinaleState.get(serverPlayer.level().getServer()).complete());
        ServerPlayNetworking.send(serverPlayer, new FriendDialoguePayload(
                definition.id(), definition.name(), definition.hint(),
                routeReaction.isEmpty()
                        ? definition.reaction().select(progress.visited(), progress.cleared())
                        : routeReaction,
                definition.lines()));
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public boolean hurtServer(ServerLevel world, DamageSource source, float amount) {
        // These authored residents are placed once with their districts. Losing one to
        // combat or an area attack would permanently remove its conversation.
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput view) {
        super.readAdditionalSaveData(view);
        getEntityData().set(FRIEND_ID, view.getStringOr("FriendId", ""));
        profileInitialized = false;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput view) {
        super.addAdditionalSaveData(view);
        view.putString("FriendId", friendId());
    }

    @Override
    public boolean isPersistenceRequired() {
        return true;
    }

    private void applyName(FriendDefinition definition) {
        if (getCustomName() == null) {
            setCustomName(Component.literal(definition.name()));
        }
        setCustomNameVisible(true);
    }
}
