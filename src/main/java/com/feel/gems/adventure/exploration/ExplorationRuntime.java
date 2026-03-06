package com.feel.gems.adventure.exploration;

import com.feel.gems.adventure.AdventureSounds;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** Three independent, reusable exploration puzzles at generated side sites. */
public final class ExplorationRuntime {
    private ExplorationRuntime() { }

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            Block block = world.getBlockState(hit.getBlockPos()).getBlock();
            if (!isMarker(block)) return InteractionResult.PASS;
            if (!(world instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.SUCCESS;
            }
            if (level.dimension() != Level.OVERWORLD) {
                tell(serverPlayer, "These field markers belong in the Overworld.");
                return InteractionResult.SUCCESS_SERVER;
            }
            return use(level, serverPlayer, hit.getBlockPos(), block);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                ExplorationRewards.reconcileOnJoin(handler.getPlayer()));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 100 != 0) return;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                ExplorationRewards.deliverPending(player);
            }
        });
    }

    private static boolean isMarker(Block block) {
        return block == ExplorationBlocks.TRAIL_ORIGIN || block == ExplorationBlocks.TRAIL_TURN
                || block == ExplorationBlocks.TRAIL_FINISH || block == ExplorationBlocks.CIPHER_PLAQUE
                || block == ExplorationBlocks.CIPHER_SUN || block == ExplorationBlocks.CIPHER_STAR
                || block == ExplorationBlocks.CIPHER_MOON || block == ExplorationBlocks.CIPHER_SEAL
                || block == ExplorationBlocks.VISTA_DIAL;
    }

    private static InteractionResult use(ServerLevel level, ServerPlayer player, BlockPos pos, Block block) {
        ExplorationState state = ExplorationState.get(level.getServer());
        if (player.isShiftKeyDown()) {
            return fieldRest(level, player, state);
        }
        if (block == ExplorationBlocks.TRAIL_ORIGIN) {
            state.startTrail(player.getUUID(), pos.asLong());
            tell(player, "A blue stone ribbon leaves the first marker and bends out of sight.");
        } else if (block == ExplorationBlocks.TRAIL_TURN) {
            BlockPos origin = trailOrigin(state, player);
            if (origin == null || !near(origin, pos, 80)) {
                tell(player, "The blue ribbon at this bend points back toward its first stone.");
            } else {
                state.markTrailTurn(player.getUUID());
                tell(player, "The bend is marked. The stone ribbon continues.");
            }
        } else if (block == ExplorationBlocks.TRAIL_FINISH) {
            finishTrail(level, player, pos, state);
        } else if (block == ExplorationBlocks.CIPHER_PLAQUE) {
            state.startCipher(player.getUUID(), pos.asLong());
            tell(player, "Three symbols circle the seal. The worn inscription names their changing places in the sky.");
        } else if (block == ExplorationBlocks.CIPHER_SEAL) {
            finishCipher(level, player, pos, state);
        } else if (block == ExplorationBlocks.VISTA_DIAL) {
            finishVista(level, player, pos);
        } else {
            int symbol = block == ExplorationBlocks.CIPHER_SUN ? 0
                    : block == ExplorationBlocks.CIPHER_STAR ? 2 : 1;
            BlockPos plaque = cipherPlaque(state, player);
            if (plaque == null || !near(plaque, pos, 48)) {
                tell(player, "A worn plaque faces these symbols. Its lines point to the sky.");
            } else {
                int step = state.pressCipher(player.getUUID(), symbol);
                tell(player, step == 0 ? "The symbols dim. The pattern has returned to its beginning."
                        : step == 3 ? "All three symbols glow around the seal."
                        : step + "/3 symbols aligned.");
            }
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    private static void finishTrail(ServerLevel level, ServerPlayer player, BlockPos finish,
                                    ExplorationState state) {
        BlockPos origin = trailOrigin(state, player);
        if (origin == null || !level.getBlockState(origin).is(ExplorationBlocks.TRAIL_ORIGIN)
                || !near(origin, finish, 80) || horizontalDistanceSquared(origin, finish) < 32 * 32) {
            tell(player, "The far stone's blue ribbon leads back toward an older marker.");
            return;
        }
        if (state.model().journey(player.getUUID()).trailStep() == 0) {
            tell(player, "A gap remains in the blue ribbon where the trail turns.");
            return;
        }
        if (complete(level, player, "trail", origin)) state.clearTrail(player.getUUID());
    }

    private static void finishCipher(ServerLevel level, ServerPlayer player, BlockPos seal,
                                     ExplorationState state) {
        BlockPos plaque = cipherPlaque(state, player);
        if (plaque == null || !level.getBlockState(plaque).is(ExplorationBlocks.CIPHER_PLAQUE)
                || !near(plaque, seal, 48)) {
            tell(player, "A worn plaque beside the seal bears the same three symbols.");
            return;
        }
        if (state.model().journey(player.getUUID()).cipherStep() != 3) {
            tell(player, "The seal is quiet. Its three symbols have not all answered yet.");
            return;
        }
        if (complete(level, player, "cipher", plaque)) state.clearCipher(player.getUUID());
    }

    private static void finishVista(ServerLevel level, ServerPlayer player, BlockPos dial) {
        if (!player.getMainHandItem().is(Items.SPYGLASS)
                && !player.getOffhandItem().is(Items.SPYGLASS)) {
            tell(player, "A spyglass fits the dial's sighting rest. The eastern horizon is carved into its rim.");
            return;
        }
        if (player.getDirection() != Direction.EAST || !level.canSeeSky(dial.above())) {
            tell(player, "The roof interrupts the sightline, or the dawn carving is behind you.");
            return;
        }
        complete(level, player, "vista", dial);
    }

    private static boolean complete(ServerLevel level, ServerPlayer player, String kind, BlockPos origin) {
        // A disconnected, dead or spectating player cannot finish a site or award nearby players.
        // This can happen if another callback invokes a marker while logout/death is in progress.
        if (!player.isAlive() || player.isSpectator() || !level.players().contains(player)) return false;
        ExplorationState state = ExplorationState.get(level.getServer());
        var nearbyCompanions = level.players().stream()
                .filter(other -> other.isAlive() && !other.isSpectator()
                        && other.distanceToSqr(player) <= 24 * 24
                        && (other == player || ExplorationParticipation.ready(kind, origin.asLong(),
                                state.model().journey(other.getUUID()),
                                other.getMainHandItem().is(Items.SPYGLASS)
                                        || other.getOffhandItem().is(Items.SPYGLASS),
                                other.getDirection() == Direction.EAST,
                                level.canSeeSky(other.blockPosition().above()),
                                Math.abs(other.getBlockY() - origin.getY()) <= 4)))
                .toList();
        var result = ExplorationRewards.complete(level,
                ExplorationProgress.activityId(kind, origin.getX(), origin.getZ()),
                origin, nearbyCompanions);
        if (result.firstPlayerClears().isEmpty()) {
            tell(player, "You have already solved this site. Others can still discover it.");
        } else {
            level.playSound(null, player.blockPosition(), AdventureSounds.STORY_RECORD_SEAL,
                    SoundSource.BLOCKS, 0.7F, 1.1F);
        }
        return true;
    }

    private static InteractionResult fieldRest(ServerLevel level, ServerPlayer player,
                                               ExplorationState state) {
        if (state.model().pending(player.getUUID()).isEmpty()) {
            tell(player, "Clear one field site to unlock the travel rest at any marker.");
            return InteractionResult.SUCCESS_SERVER;
        }
        ExplorationDeliveryAccount account = ExplorationDeliveryAccount.of(player);
        long now = level.getGameTime();
        long remaining = account.fieldReadyAt() - now;
        if (remaining > 0 && remaining <= 20L * 600) {
            tell(player, "The travel rest is ready in " + ((remaining + 19) / 20) + " seconds.");
            return InteractionResult.SUCCESS_SERVER;
        }
        account.setFieldReadyAt(now + 20L * 600);
        boolean surveyor = state.model().hasEstateMastery(player.getUUID());
        int duration = surveyor ? 20 * 180 : 20 * 90;
        player.addEffect(new MobEffectInstance(MobEffects.SPEED, duration, 0));
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, duration, 0));
        level.playSound(null, player.blockPosition(), AdventureSounds.STORY_PAGE_TURN,
                SoundSource.PLAYERS, 0.55F, 1.2F);
        tell(player, "Travel rest: move and see clearly for "
                + (surveyor ? "three minutes." : "90 seconds."));
        return InteractionResult.SUCCESS_SERVER;
    }

    private static BlockPos trailOrigin(ExplorationState state, ServerPlayer player) {
        long encoded = state.model().journey(player.getUUID()).trailOrigin();
        return encoded == ExplorationProgress.Journey.NONE ? null : BlockPos.of(encoded);
    }

    private static BlockPos cipherPlaque(ExplorationState state, ServerPlayer player) {
        long encoded = state.model().journey(player.getUUID()).cipherPlaque();
        return encoded == ExplorationProgress.Journey.NONE ? null : BlockPos.of(encoded);
    }

    private static boolean near(BlockPos a, BlockPos b, int radius) {
        return Math.abs((long) a.getX() - b.getX()) <= radius
                && Math.abs((long) a.getY() - b.getY()) <= 24
                && Math.abs((long) a.getZ() - b.getZ()) <= radius;
    }

    private static long horizontalDistanceSquared(BlockPos a, BlockPos b) {
        long dx = (long) a.getX() - b.getX();
        long dz = (long) a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    private static void tell(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message), false);
    }
}
