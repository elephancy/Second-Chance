package com.feel.gems.adventure.maze;

import com.feel.gems.adventure.AdventureSounds;
import com.feel.gems.adventure.exploration.ExplorationProgress;
import com.feel.gems.adventure.exploration.ExplorationRewards;
import com.feel.gems.state.GemsPersistentDataHolder;
import com.feel.gems.util.GemsTeleport;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.List;

/** Event-only, player-owned puzzle state. No per-tick maze scan is required. */
public final class MazeRuntime {
    private static final String ROOT = "NinefoldMaze";
    private static final String ENTRY = "entry";
    private static final String RETURN = "return";
    private static final String GLYPH = "glyph";
    private static final String MIRROR = "mirror";
    private static final String WITNESSES = "witnesses";
    private static final String CHECKPOINTS = "checkpoints";
    private static final String TRAP_TICK = "trapTick";
    private static final int ALL_CHECKPOINTS = 7;

    private MazeRuntime() {
    }

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            Block block = world.getBlockState(hit.getBlockPos()).getBlock();
            if (!isControl(block)) return InteractionResult.PASS;
            if (!(world instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.SUCCESS;
            }
            if (level.dimension() != Level.OVERWORLD) return InteractionResult.SUCCESS_SERVER;
            BlockPos pos = hit.getBlockPos();
            if (block == MazeBlocks.ENTRY) {
                enter(serverPlayer, pos);
                return InteractionResult.SUCCESS_SERVER;
            }
            CompoundTag data = data(serverPlayer);
            BlockPos origin = origin(data);
            if (origin == null || !inMaze(origin, pos)) {
                tell(serverPlayer, "Use the maze entrance before working its route.");
                return InteractionResult.SUCCESS_SERVER;
            }
            if (block == MazeBlocks.RESET) {
                reset(level, serverPlayer, data);
                return InteractionResult.SUCCESS_SERVER;
            }
            if (block == MazeBlocks.EXIT) {
                exit(level, serverPlayer, data, origin);
                return InteractionResult.SUCCESS_SERVER;
            }
            if (block == MazeBlocks.SUN || block == MazeBlocks.MOON || block == MazeBlocks.STAR) {
                glyph(level, serverPlayer, data, block);
            } else if (block == MazeBlocks.MIRROR_AMBER || block == MazeBlocks.MIRROR_BLUE) {
                mirror(level, serverPlayer, data, block);
            } else if (block == MazeBlocks.WITNESS_ONE || block == MazeBlocks.WITNESS_TWO
                    || block == MazeBlocks.WITNESS_THREE) {
                witness(level, serverPlayer, data, block);
            } else {
                checkpoint(level, serverPlayer, data, block);
            }
            return InteractionResult.SUCCESS_SERVER;
        });
    }

    private static boolean isControl(Block block) {
        return block == MazeBlocks.ENTRY || block == MazeBlocks.EXIT || block == MazeBlocks.RESET
                || block == MazeBlocks.SUN || block == MazeBlocks.MOON || block == MazeBlocks.STAR
                || block == MazeBlocks.MIRROR_AMBER || block == MazeBlocks.MIRROR_BLUE
                || block == MazeBlocks.WITNESS_ONE || block == MazeBlocks.WITNESS_TWO
                || block == MazeBlocks.WITNESS_THREE || block == MazeBlocks.CHECKPOINT_ONE
                || block == MazeBlocks.CHECKPOINT_TWO || block == MazeBlocks.CHECKPOINT_THREE;
    }

    private static CompoundTag data(ServerPlayer player) {
        CompoundTag root = ((GemsPersistentDataHolder) player).gems$getPersistentData();
        return root.getCompound(ROOT).orElseGet(() -> {
            CompoundTag created = new CompoundTag();
            root.put(ROOT, created);
            return created;
        });
    }

    private static BlockPos origin(CompoundTag data) {
        return data.contains(ENTRY) ? BlockPos.of(data.getLongOr(ENTRY, 0L)) : null;
    }

    private static boolean inMaze(BlockPos entry, BlockPos marker) {
        long dx = (long) entry.getX() - marker.getX();
        long dz = (long) entry.getZ() - marker.getZ();
        return dx * dx + dz * dz <= 210L * 210L
                && Math.abs((long) entry.getY() - marker.getY()) <= 48;
    }

    private static void enter(ServerPlayer player, BlockPos pos) {
        CompoundTag data = data(player);
        if (!data.contains(ENTRY) || data.getLongOr(ENTRY, 0L) != pos.asLong()) {
            data.putLong(ENTRY, pos.asLong());
            data.putInt(GLYPH, 0);
            data.putInt(MIRROR, 0);
            data.putInt(WITNESSES, 0);
            data.putInt(CHECKPOINTS, 0);
            data.putLong(RETURN, player.blockPosition().asLong());
            tell(player, "The Ninefold Maze begins. Three floors, three seals. Each floor keeps part of its own account.");
        } else {
            tell(player, "Your last safe point is kept. Use a copper reset lamp if you are lost.");
        }
    }

    private static void glyph(ServerLevel level, ServerPlayer player, CompoundTag data, Block block) {
        int step = data.getIntOr(GLYPH, 0);
        if (step == 3) {
            tell(player, "The first seal is already open. Amber glass glows above.");
            return;
        }
        Block expected = step == 0 ? MazeBlocks.SUN : step == 1 ? MazeBlocks.MOON : MazeBlocks.STAR;
        if (block != expected) {
            data.putInt(GLYPH, 0);
            tell(player, "The glyphs went dark. This floor's sequence has returned to its beginning.");
            level.playSound(null, player.blockPosition(), AdventureSounds.MAZE_TRAP,
                    SoundSource.BLOCKS, 0.5F, 0.8F);
            return;
        }
        data.putInt(GLYPH, step + 1);
        tell(player, step == 2 ? "First seal open. A blue checkpoint glows nearby."
                : "Glyph " + (step + 1) + " of 3 lit.");
        chime(level, player);
    }

    private static void mirror(ServerLevel level, ServerPlayer player, CompoundTag data, Block block) {
        if (data.getIntOr(GLYPH, 0) != 3) {
            tell(player, "The mirror floor waits for the sky seal below.");
            return;
        }
        int step = data.getIntOr(MIRROR, 0);
        if (step == 2) {
            tell(player, "The mirror route is already aligned. The upper witness marks have stirred.");
            return;
        }
        Block expected = step == 0 ? MazeBlocks.MIRROR_AMBER : MazeBlocks.MIRROR_BLUE;
        if (block != expected) {
            data.putInt(MIRROR, 0);
            tell(player, "The reflection broke. The mirror pair has returned to its beginning.");
            return;
        }
        data.putInt(MIRROR, step + 1);
        tell(player, step == 0 ? "One reflection aligned. Its partner is still dark." : "Second seal open. The next blue checkpoint glows.");
        chime(level, player);
    }

    private static void witness(ServerLevel level, ServerPlayer player, CompoundTag data, Block block) {
        if (data.getIntOr(MIRROR, 0) != 2) {
            tell(player, "The witness floor waits for both reflections below.");
            return;
        }
        int bit = block == MazeBlocks.WITNESS_ONE ? 1 : block == MazeBlocks.WITNESS_TWO ? 2 : 4;
        int found = data.getIntOr(WITNESSES, 0) | bit;
        data.putInt(WITNESSES, found);
        tell(player, found == 7 ? "All three witnesses found. The last checkpoint and exit are open."
                : Integer.bitCount(found) + " of 3 witnesses found.");
        chime(level, player);
    }

    private static void checkpoint(ServerLevel level, ServerPlayer player, CompoundTag data, Block block) {
        int stage = block == MazeBlocks.CHECKPOINT_ONE ? 1
                : block == MazeBlocks.CHECKPOINT_TWO ? 2 : 3;
        boolean ready = switch (stage) {
            case 1 -> data.getIntOr(GLYPH, 0) == 3;
            case 2 -> data.getIntOr(MIRROR, 0) == 2;
            default -> data.getIntOr(WITNESSES, 0) == 7;
        };
        if (!ready) {
            tell(player, switch (stage) {
                case 1 -> "The sky glyphs still shine out of step.";
                case 2 -> "The two reflections have not both reached this stone.";
                default -> "The last blue stone still has dark witness marks.";
            });
            return;
        }
        data.putInt(CHECKPOINTS, data.getIntOr(CHECKPOINTS, 0) | (1 << (stage - 1)));
        data.putLong(RETURN, player.blockPosition().asLong());
        tell(player, "Checkpoint " + stage + " saved. A copper reset lamp can bring you back here.");
        chime(level, player);
    }

    private static void exit(ServerLevel level, ServerPlayer player, CompoundTag data, BlockPos origin) {
        if (!readyToExit(data)) {
            tell(player, "The last door stays shut. Unfinished marks still wait across the three floors.");
            return;
        }
        String id = ExplorationProgress.activityId("maze", origin.getX(), origin.getZ());
        List<ServerPlayer> party = level.getServer().getPlayerList().getPlayers().stream()
                .filter(other -> other.level() == level
                        && other.blockPosition().distSqr(player.blockPosition()) <= 16L * 16L)
                .filter(other -> {
                    CompoundTag progress = data(other);
                    return origin.equals(origin(progress)) && readyToExit(progress);
                }).toList();
        ExplorationRewards.complete(level, id, origin, party);
        tell(player, "The Ninefold Maze is cleared for " + party.size()
                + (party.size() == 1 ? " traveler." : " travelers."));
        chime(level, player);
    }

    private static boolean readyToExit(CompoundTag data) {
        return data.getIntOr(GLYPH, 0) == 3 && data.getIntOr(MIRROR, 0) == 2
                && data.getIntOr(WITNESSES, 0) == 7
                && data.getIntOr(CHECKPOINTS, 0) == ALL_CHECKPOINTS;
    }

    private static void reset(ServerLevel level, ServerPlayer player, CompoundTag data) {
        if (!data.contains(RETURN)) {
            tell(player, "Use the maze entrance to set your first safe point.");
            return;
        }
        BlockPos feet = BlockPos.of(data.getLongOr(RETURN, 0L));
        if (!safe(level, feet) || !GemsTeleport.teleport(player, level,
                feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5,
                player.getYRot(), player.getXRot())) {
            tell(player, "That checkpoint is blocked or unloaded. Walk back to the last open passage.");
            return;
        }
        tell(player, "Back at your last safe point. Your solved seals are kept.");
        level.playSound(null, feet, AdventureSounds.MAZE_RESET, SoundSource.BLOCKS, 0.6F, 1.0F);
    }

    private static boolean safe(ServerLevel level, BlockPos feet) {
        if (!level.hasChunkAt(feet) || !level.hasChunkAt(feet.below())
                || !level.hasChunkAt(feet.above()) || !level.getWorldBorder().isWithinBounds(feet)) return false;
        var support = level.getBlockState(feet.below());
        return !support.getCollisionShape(level, feet.below()).isEmpty()
                && support.getBlock() != MazeBlocks.TRAP_RETURN
                && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
                && level.getFluidState(feet).isEmpty() && level.getFluidState(feet.above()).isEmpty();
    }

    static void triggerTrap(ServerPlayer player, BlockPos pos, MazeBlocks.MazeTrap effect) {
        if (!(player.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        CompoundTag data = data(player);
        BlockPos origin = origin(data);
        if (origin == null || !inMaze(origin, pos)) return;
        long now = level.getGameTime();
        if (now - data.getLongOr(TRAP_TICK, Long.MIN_VALUE / 2) < 40) return;
        data.putLong(TRAP_TICK, now);
        switch (effect) {
            case ECHO -> {
                player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 55, 0));
                tell(player, "An echo closes in. A violet ring glows on the tile beneath you.");
            }
            case DART -> {
                if (player.getHealth() > 4.0F) {
                    player.hurtServer(level, level.damageSources().magic(), 1.0F);
                }
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
                player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 45, 0));
                tell(player, "The copper darts nicked you. A red ring marks the tile beneath you.");
            }
            case SNARE -> {
                player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 85, 2));
                tell(player, "The floor caught your boots. A green ring marks the tile beneath you.");
            }
            case RETURN -> {
                tell(player, "The false door folded back to your checkpoint.");
                reset(level, player, data);
            }
        }
        if (effect != MazeBlocks.MazeTrap.RETURN) {
            level.playSound(null, pos, AdventureSounds.MAZE_TRAP, SoundSource.BLOCKS, 0.5F, 0.8F);
        }
    }

    private static void chime(ServerLevel level, ServerPlayer player) {
        level.playSound(null, player.blockPosition(), AdventureSounds.MAZE_SEAL,
                SoundSource.BLOCKS, 0.6F, 1.15F);
    }

    private static void tell(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message), false);
    }
}
