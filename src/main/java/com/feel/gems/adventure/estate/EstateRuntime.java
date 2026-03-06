package com.feel.gems.adventure.estate;

import com.feel.gems.adventure.exploration.ExplorationProgress;
import com.feel.gems.adventure.exploration.ExplorationRewards;
import com.feel.gems.adventure.exploration.ExplorationState;
import com.feel.gems.state.GemsPersistentDataHolder;
import com.feel.gems.util.GemsTeleport;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** Server-owned estate entry, checkpoint, central clear and one-way return. */
public final class EstateRuntime {
    private static final String ROOT = "FourRoadsEstate";
    private static final String ENTRY = "entry";
    private static final String FOYER = "foyer";
    private static final String CHECKPOINT = "checkpoint";
    private static final String WING = "wing";
    private static final String WON = "won";
    private static final String TRAP_TICK = "trapTick";
    private static final String HINT_TICK = "hintTick";
    private static final String ENTRY_HINT_TICK = "entryHintTick";
    private static volatile BiConsumer<ServerPlayer, BlockPos> foyerUseObserver;

    private EstateRuntime() { }

    /** Register once after EstateBlocks.init(), alongside AdventureEvents. */
    public static void register() {
        EstateRouteReadiness.register();
        EstateReturnService.register();
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            Block block = world.getBlockState(hit.getBlockPos()).getBlock();
            if (!EstateBlocks.isControl(block)) return InteractionResult.PASS;
            if (!(world instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.SUCCESS;
            }
            if (level.dimension() != Level.OVERWORLD) return InteractionResult.SUCCESS_SERVER;
            BlockPos pos = hit.getBlockPos();
            char wing = EstateBlocks.wing(block);
            if (wing != '?') enter(serverPlayer, pos, wing);
            else if (block == EstateBlocks.FOYER) {
                reachFoyer(serverPlayer, pos);
                BiConsumer<ServerPlayer, BlockPos> observer = foyerUseObserver;
                if (observer != null) observer.accept(serverPlayer, pos);
            }
            else if (block == EstateBlocks.RESET) checkpoint(serverPlayer, pos);
            else if (block == EstateBlocks.RETURN) returnOutside(serverPlayer, pos);
            return InteractionResult.SUCCESS_SERVER;
        });
    }

    /** Inert unless an opt-in connected fixture observes the production use path. */
    static void setFoyerUseObserver(BiConsumer<ServerPlayer, BlockPos> observer) {
        foyerUseObserver = observer;
    }

    static void enter(ServerPlayer player, BlockPos pos, char wing) {
        enter(player, pos, wing, false);
    }

    static void enterOnStep(ServerPlayer player, BlockPos pos, char wing) {
        enter(player, pos, wing, true);
    }

    private static void enter(ServerPlayer player, BlockPos pos, char wing, boolean stepped) {
        if (!(player.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        if (EstateBlocks.wing(level.getBlockState(pos).getBlock()) != wing) return;
        CompoundTag data = data(player);
        long now = level.getGameTime();
        EstateRouteSite.Site site = EstateRouteSite.readLoaded(level, pos);
        if (site == null
                || EstateSiteIntegrity.inspectEntryLoaded(level, site, pos, wing)
                        != EstateSiteIntegrity.Result.READY) {
            tell(player, "This road is still being surveyed. Try again when its nearby chunks are ready.");
            return;
        }
        if (!EstateRouteReadiness.begin(player, site, pos, wing)) return;
        BlockPos foyer = site.foyer();
        boolean changed = !data.contains(ENTRY) || data.getLongOr(ENTRY, 0L) != pos.asLong()
                || data.getLongOr(FOYER, 0L) != foyer.asLong();
        if (!changed) {
            long lastHint = data.getLongOr(ENTRY_HINT_TICK, Long.MIN_VALUE / 2);
            if (stepped && now >= lastHint && now - lastHint < 100) return;
            data.putLong(ENTRY_HINT_TICK, now);
            tell(player, "Four passages stand between this gate and the mansion. The road remembers footsteps.");
            return;
        }
        boolean sameEstate = data.contains(FOYER) && data.getLongOr(FOYER, 0L) == foyer.asLong();
        data.putLong(ENTRY, pos.asLong());
        data.putLong(FOYER, foyer.asLong());
        data.putLong(CHECKPOINT, pos.above().asLong());
        data.putInt(WING, wing);
        if (!sameEstate) data.putBoolean(WON, false);
        data.putLong(ENTRY_HINT_TICK, now);
        tell(player, "You found the " + wingName(wing)
                + ". Reach the house at the middle. Any road is enough.");
    }

    static void reachFoyer(ServerPlayer player, BlockPos pos) {
        if (!(player.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        if (level.getBlockState(pos).getBlock() != EstateBlocks.FOYER) return;
        EstateRouteSite.Site site = EstateRouteSite.readLoaded(level, pos);
        if (site == null || !site.foyer().equals(pos)
                || EstateSiteIntegrity.inspectFoyerLoaded(level, site, pos)
                        != EstateSiteIntegrity.Result.READY) {
            tell(player, "The foyer is not ready. Leave by an open road and try again later.");
            return;
        }
        CompoundTag own = data(player);
        if (!sameEstate(own, pos)) {
            long now = level.getGameTime();
            if (now - own.getLongOr(HINT_TICK, Long.MIN_VALUE / 2) >= 100) {
                own.putLong(HINT_TICK, now);
                tell(player, "The foyer has no sign you came in from an outer road.");
            }
            return;
        }
        if (EstateRouteReadiness.completedWing(player, site) == '?') {
            tell(player, "Some passages on your road have not heard you pass.");
            return;
        }
        List<ServerPlayer> eligible = new ArrayList<>();
        for (ServerPlayer other : level.players()) {
            if (other.isAlive() && !other.isSpectator()
                    && other.blockPosition().distSqr(pos) <= 8L * 8L
                    && sameEstate(data(other), pos)
                    && EstateRouteReadiness.completedWing(other, site) != '?') {
                eligible.add(other);
            }
        }
        if (eligible.isEmpty()) return;
        List<ServerPlayer> firstWinners = eligible.stream()
                .filter(other -> !data(other).getBooleanOr(WON, false)).toList();
        boolean newlyEarned = !firstWinners.isEmpty();
        if (newlyEarned) {
            String id = ExplorationProgress.activityId("estate", pos.getX(), pos.getZ());
            ExplorationRewards.complete(level, id, pos, firstWinners);
            for (ServerPlayer other : firstWinners) {
                data(other).putBoolean(WON, true);
                tell(other, "You reached the house at the middle. The return stone takes you outside.");
            }
        }
        ExplorationProgress progress = ExplorationState.get(level.getServer()).model();
        for (char wing : new char[]{'N', 'E', 'S', 'W'}) {
            String roadId = ExplorationProgress.estateRoadId(wing, pos.getX(), pos.getZ());
            List<ServerPlayer> firstSurvey = eligible.stream()
                    .filter(other -> EstateRouteReadiness.completedWing(other, site) == wing
                            && !progress.earned(other.getUUID(), roadId))
                    .toList();
            if (firstSurvey.isEmpty()) continue;
            newlyEarned = true;
            ExplorationRewards.complete(level, roadId, pos, firstSurvey);
            for (ServerPlayer other : firstSurvey) {
                int count = progress.estateRoadCount(other.getUUID(), pos.getX(), pos.getZ());
                tell(other, wingName(wing) + " surveyed: " + count
                        + "/4 roads. Its cache is yours once, even after a restart.");
                if (count == 4 && !progress.hasEstateMastery(other.getUUID())) {
                    ExplorationRewards.complete(level,
                            ExplorationProgress.activityId("estate_mastery", pos.getX(), pos.getZ()),
                            pos, List.of(other));
                    tell(other, "All four roads surveyed. Your field rests now last three minutes.");
                }
            }
        }
        if (newlyEarned) {
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.BLOCKS, 0.9F, 1.2F);
        }
    }

    private static void checkpoint(ServerPlayer player, BlockPos marker) {
        if (!(player.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        EstateRouteSite.Site site = EstateRouteSite.readLoaded(level, marker);
        if (site == null || !sameEstate(data(player), site.foyer())) return;
        CompoundTag data = data(player);
        if (player.isShiftKeyDown()) {
            BlockPos feet = BlockPos.of(data.getLongOr(CHECKPOINT, 0L));
            if (safe(level, feet) && GemsTeleport.teleport(player, level,
                    feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5,
                    player.getYRot(), player.getXRot())) {
                EstateRouteReadiness.clear(player);
                tell(player, "Back at your last road marker.");
            } else {
                tell(player, "That marker is blocked. Find another open path.");
            }
            return;
        }
        BlockPos feet = player.blockPosition();
        if (!safe(level, feet)) {
            tell(player, "Stand on a clear path to save this marker.");
            return;
        }
        data.putLong(CHECKPOINT, feet.asLong());
        tell(player, "Safe point saved. Sneak-use a road marker to return here.");
    }

    private static void returnOutside(ServerPlayer player, BlockPos marker) {
        if (!(player.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        if (level.getBlockState(marker).getBlock() != EstateBlocks.RETURN) return;
        EstateRouteSite.Site site = EstateRouteSite.readLoaded(level, marker);
        if (site == null
                || !EstateSiteIntegrity.clearLanding(level, marker)) return;
        // Creative-placed copies cannot become teleports.
        BlockPos foyer = site.foyer();
        if (marker.distSqr(foyer) > 8L * 8L) return;
        CompoundTag data = data(player);
        BlockPos preferred = sameEstate(data, foyer)
                ? BlockPos.of(data.getLongOr(ENTRY, 0L)) : null;
        EstateReturnService.request(player, level, site, marker, preferred);
    }

    static void triggerTrap(ServerPlayer player, BlockPos pos, char wing) {
        if (!(player.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        EstateRouteSite.Site site = EstateRouteSite.readLoaded(level, pos);
        if (site == null || !sameEstate(data(player), site.foyer())) return;
        CompoundTag data = data(player);
        long now = level.getGameTime();
        if (now - data.getLongOr(TRAP_TICK, Long.MIN_VALUE / 2) < 40) return;
        data.putLong(TRAP_TICK, now);
        switch (wing) {
            case 'N' -> {
                player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 70, 1));
                tell(player, "Roots caught your feet. Follow the lit stones.");
            }
            case 'E' -> {
                player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 45, 0));
                tell(player, "The mirror path went dark. Wait for the glint to return.");
            }
            case 'S' -> {
                player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 65, 0));
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 65, 0));
                tell(player, "The sluice pushed back. A dry path is nearby.");
            }
            case 'W' -> {
                if (player.getHealth() > 4.0F) {
                    player.hurtServer(level, level.damageSources().magic(), 1.0F);
                }
                tell(player, "A clockwork dart grazed you. Its lamp gives warning.");
            }
            default -> { return; }
        }
        level.playSound(null, pos, SoundEvents.DEEPSLATE_BREAK,
                SoundSource.BLOCKS, 0.5F, 0.9F);
    }

    private static boolean sameEstate(CompoundTag data, BlockPos foyer) {
        return data.contains(ENTRY) && data.contains(FOYER)
                && data.getLongOr(FOYER, 0L) == foyer.asLong();
    }

    private static boolean safe(ServerLevel level, BlockPos feet) {
        if (!level.getWorldBorder().isWithinBounds(feet)
                || !level.hasChunkAt(feet) || !level.hasChunkAt(feet.below())
                || !level.hasChunkAt(feet.above())) return false;
        return !level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty()
                && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
                && level.getFluidState(feet).isEmpty()
                && level.getFluidState(feet.above()).isEmpty();
    }

    private static CompoundTag data(ServerPlayer player) {
        CompoundTag root = ((GemsPersistentDataHolder) player).gems$getPersistentData();
        return root.getCompound(ROOT).orElseGet(() -> {
            CompoundTag created = new CompoundTag();
            root.put(ROOT, created);
            return created;
        });
    }

    private static String wingName(char wing) {
        return switch (wing) {
            case 'N' -> "Garden Road";
            case 'E' -> "Mirror Road";
            case 'S' -> "Sluice Road";
            case 'W' -> "Clock Road";
            default -> "Four Roads Estate";
        };
    }

    private static void tell(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message), false);
    }
}
