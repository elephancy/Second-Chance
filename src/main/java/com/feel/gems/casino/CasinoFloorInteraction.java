package com.feel.gems.casino;

import com.feel.gems.friend.FriendEntity;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.phys.AABB;

/** The six signed bays are usable seats; Ragab remains the shared host. */
public final class CasinoFloorInteraction {
    private static final double HOST_RADIUS = 24;

    private CasinoFloorInteraction() { }

    static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            BlockPos signPos = hit.getBlockPos();
            CasinoGame game = gameAt(world.getBlockEntity(signPos) instanceof SignBlockEntity sign ? sign : null);
            if (game == null) return InteractionResult.PASS;
            if (!(world instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.SUCCESS;
            }
            FriendEntity ragab = level.getEntitiesOfClass(FriendEntity.class,
                    new AABB(signPos).inflate(HOST_RADIUS),
                    friend -> friend.isAlive() && "ragab".equals(friend.friendId()))
                    .stream().filter(friend -> friend.distanceToSqr(
                            signPos.getX() + 0.5, signPos.getY() + 0.5, signPos.getZ() + 0.5)
                            <= HOST_RADIUS * HOST_RADIUS)
                    .findFirst().orElse(null);
            if (ragab == null) return InteractionResult.PASS;
            CasinoService.openAtTable(serverPlayer, ragab, game, signPos);
            return InteractionResult.SUCCESS_SERVER;
        });
    }

    static boolean matches(ServerLevel level, BlockPos pos, CasinoGame game) {
        return gameAt(level.getBlockEntity(pos) instanceof SignBlockEntity sign ? sign : null) == game;
    }

    static boolean isTable(ServerLevel level, BlockPos pos) {
        return gameAt(level.getBlockEntity(pos) instanceof SignBlockEntity sign ? sign : null) != null;
    }

    private static CasinoGame gameAt(SignBlockEntity sign) {
        if (sign == null) return null;
        var lines = sign.getText(SignTextSlot.FRONT).getMessages(false);
        String title = lines.get(0).getString();
        String hint = lines.get(1).getString();
        String stake = lines.get(2).getString();
        return switch (title) {
            case "RPS TOURNAMENT" -> "First to two".equals(hint) && "Stake 3".equals(stake) ? CasinoGame.RPS : null;
            case "TWENTY-ONE" -> "Hit or stand".equals(hint) && "Stake 4".equals(stake) ? CasinoGame.CARDS : null;
            case "COLOR WHEEL" -> "Pick color/half".equals(hint) && "Stake 2".equals(stake) ? CasinoGame.WHEEL : null;
            case "HOLD AND SPIN" -> "Keep a symbol".equals(hint) && "Stake 3".equals(stake) ? CasinoGame.REELS : null;
            case "TRIVIA" -> "One question".equals(hint) && "Stake 2".equals(stake) ? CasinoGame.TRIVIA : null;
            case "SIGNAL TABLE" -> "Two players".equals(hint) && "Stake 2 each".equals(stake) ? CasinoGame.COOP : null;
            default -> null;
        };
    }
}
