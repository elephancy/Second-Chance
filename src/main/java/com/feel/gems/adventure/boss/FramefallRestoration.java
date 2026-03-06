package com.feel.gems.adventure.boss;

import com.feel.gems.particle.ModParticles;
import com.feel.gems.sound.ModSounds;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.BlockPos;

/** Restores only intact authored gallery panels; safe to retry after a partial save. */
final class FramefallRestoration {
    private static final int[] PANEL_X = {11, 35};
    private static final int[] PANEL_Z = {12, 22, 32};

    private FramefallRestoration() { }

    static int revealPanels(ServerLevel world, BlockPos courtCenter) {
        int rotation = findOrientation(world, courtCenter);
        if (rotation < 0) return 0;
        int changed = 0;
        for (int x : PANEL_X) {
            for (int z : PANEL_Z) {
                if (!world.getBlockState(at(courtCenter, x + 1, 10, z, rotation))
                        .is(Blocks.CONCRETE.yellow())) continue;
                boolean changedPanel = false;
                for (int y = 8; y <= 11; y++) {
                    for (int side = -2; side <= 2; side++) {
                        if ((y == 9 || y == 10) && Math.abs(side) <= 1) continue;
                        BlockPos panel = at(courtCenter, x + side, y, z, rotation);
                        if (!world.getBlockState(panel).is(Blocks.STAINED_GLASS.blue())) continue;
                        world.setBlock(panel, Blocks.STAINED_GLASS.purple().defaultBlockState(), Block.UPDATE_ALL);
                        changed++;
                        changedPanel = true;
                    }
                }
                if (changedPanel) {
                    BlockPos canvas = at(courtCenter, x, 10, z, rotation);
                    world.sendParticles(ModParticles.GEM_STAR,
                            canvas.getX() + 0.5, canvas.getY() + 0.5, canvas.getZ() + 0.5,
                            12, 0.65, 0.8, 0.65, 0.04);
                }
            }
        }
        if (changed > 0) {
            world.playSound(null, courtCenter.getX() + 0.5, courtCenter.getY() + 1.0,
                    courtCenter.getZ() + 0.5, ModSounds.ARCANE_CHIME, SoundSource.HOSTILE,
                    1.0F, 0.85F);
        }
        return changed;
    }

    private static int findOrientation(ServerLevel world, BlockPos center) {
        int bestRotation = -1;
        int bestScore = 1;
        boolean tied = false;
        for (int rotation = 0; rotation < 4; rotation++) {
            int score = 0;
            for (int x : PANEL_X) {
                for (int z : PANEL_Z) {
                    if (world.getBlockState(at(center, x + 1, 10, z, rotation))
                            .is(Blocks.CONCRETE.yellow())) score++;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                bestRotation = rotation;
                tied = false;
            } else if (score == bestScore) {
                tied = true;
            }
        }
        return tied ? -1 : bestRotation;
    }

    private static BlockPos at(BlockPos center, int localX, int localY, int localZ, int rotation) {
        int dx = localX - 23;
        int dz = localZ - 23;
        return switch (rotation) {
            case 0 -> center.offset(dx, localY - 5, dz);
            case 1 -> center.offset(-dz, localY - 5, dx);
            case 2 -> center.offset(-dx, localY - 5, -dz);
            default -> center.offset(dz, localY - 5, -dx);
        };
    }
}
