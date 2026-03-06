package com.feel.gems.adventure;

import com.feel.gems.adventure.CampaignRouteState.Path;
import com.feel.gems.adventure.endsource.EndSourceState;
import com.feel.gems.particle.ModParticles;
import com.feel.gems.sound.ModSounds;
import com.feel.gems.adventure.AdventureSounds;
import java.util.IdentityHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/** Route-specific shared final challenge at Unwritten Court. */
public final class RouteFinaleRuntime {
    private static final ResourceKey<Structure> COURT_STRUCTURE = ResourceKey.create(
            Registries.STRUCTURE, Identifier.fromNamespaceAndPath("gems", "unwritten_court"));
    private static final int WEST = 1;
    private static final int EAST = 2;
    private static final long WITNESS_TICKS = 20L * 75;
    private static final long RELEASE_TICKS = 20L * 180;
    private static final long REPAIR_STABILITY_TICKS = 20L * 30;
    private static final long SOURCE_WITNESS_TICKS = 20L * 105;
    private static final long SOURCE_RELEASE_TICKS = 20L * 240;
    private static final long SOURCE_REPAIR_STABILITY_TICKS = 20L * 45;
    private static final long REPAIR_STRIKE_TICKS = 20L * 10;
    private static final long REPAIR_WARNING_TICKS = 20L * 4;
    private static final long IDLE_TICKS = 20L * 600;
    private static final Map<MinecraftServer, Attempt> ACTIVE = new IdentityHashMap<>();

    enum Station { DAIS, WEST_ROOM, EAST_ROOM, WEST_MARK, EAST_MARK, ENDING }
    enum RepairHazard { NONE, WARNING, GUARDED, BREACHED }

    static final class Attempt {
        final Path path;
        final BlockPos dais;
        final BlockPos westMark;
        final BlockPos eastMark;
        final BlockPos westRoom;
        final BlockPos eastRoom;
        final boolean westFirst;
        final boolean sourceAid;
        final long started;
        long lastAction;
        long westWitnessAt = -1;
        long eastWitnessAt = -1;
        int bound;
        int held;
        int westHoldTicks;
        int eastHoldTicks;
        long firstRepairHoldAt = -1;
        long repairStrikeAt = -1;
        int repairTarget = WEST;
        boolean repairWarned;
        boolean defendedStrike;
        int releaseStep;
        boolean readyAnnounced;

        Attempt(Path path, BlockPos dais, BlockPos westMark, BlockPos eastMark,
                BlockPos westRoom, BlockPos eastRoom, long now) {
            this(path, dais, westMark, eastMark, westRoom, eastRoom, now, false);
        }

        Attempt(Path path, BlockPos dais, BlockPos westMark, BlockPos eastMark,
                BlockPos westRoom, BlockPos eastRoom, long now, boolean sourceAid) {
            this.path = path;
            this.dais = dais.immutable();
            this.westMark = westMark.immutable();
            this.eastMark = eastMark.immutable();
            this.westRoom = westRoom.immutable();
            this.eastRoom = eastRoom.immutable();
            this.westFirst = (now & 1) == 0;
            this.sourceAid = sourceAid;
            this.started = now;
            this.lastAction = now;
            if (sourceAid) {
                // The End Source carries one route-specific fact into the Court.
                // It changes the opening task without altering older save data.
                if (path == Path.REMEMBER) bound = WEST;
                if (path == Path.RELEASE) releaseStep = 1;
            }
        }

        boolean nearby(BlockPos pos) {
            return dais.distSqr(pos) <= 128 * 128;
        }

        boolean blocksOtherDais(BlockPos pos, long now) {
            return !dais.equals(pos) && now - lastAction <= IDLE_TICKS
                    && (path != Path.RELEASE || !releaseExpired(now));
        }

        boolean owns(Station station, BlockPos pos) {
            return switch (station) {
                case DAIS -> dais.equals(pos);
                case WEST_ROOM -> westRoom.equals(pos);
                case EAST_ROOM -> eastRoom.equals(pos);
                case WEST_MARK -> westMark.equals(pos);
                case EAST_MARK -> eastMark.equals(pos);
                case ENDING -> false;
            };
        }

        boolean releaseExpired(long now) {
            return now - started > releaseLimit();
        }

        boolean repairExpired(long now) {
            return firstRepairHoldAt >= 0
                    && now - firstRepairHoldAt > repairStabilityLimit();
        }

        long witnessLimit() { return sourceAid ? SOURCE_WITNESS_TICKS : WITNESS_TICKS; }
        long releaseLimit() { return sourceAid ? SOURCE_RELEASE_TICKS : RELEASE_TICKS; }
        long repairStabilityLimit() {
            return sourceAid ? SOURCE_REPAIR_STABILITY_TICKS : REPAIR_STABILITY_TICKS;
        }

        int effectiveRepairSupplies(int savedSupplies) {
            return savedSupplies | (sourceAid && path == Path.REPAIR ? WEST : 0);
        }

        void resetRepairHolds() {
            held = 0;
            westHoldTicks = 0;
            eastHoldTicks = 0;
            firstRepairHoldAt = -1;
            repairStrikeAt = -1;
            repairTarget = WEST;
            repairWarned = false;
            defendedStrike = false;
            readyAnnounced = false;
        }

        boolean repairReady(long now) {
            return held == (WEST | EAST) && defendedStrike && !repairExpired(now);
        }

        RepairHazard advanceRepairHazard(long now, int supplies, boolean guarded) {
            if (supplies != (WEST | EAST)) return RepairHazard.NONE;
            if (repairStrikeAt < 0) {
                repairStrikeAt = now + REPAIR_STRIKE_TICKS;
                return RepairHazard.NONE;
            }
            if (now >= repairStrikeAt) {
                RepairHazard result = guarded ? RepairHazard.GUARDED : RepairHazard.BREACHED;
                if (guarded) {
                    defendedStrike = true;
                } else {
                    held &= ~repairTarget;
                    if (repairTarget == WEST) westHoldTicks = 0;
                    else eastHoldTicks = 0;
                    readyAnnounced = false;
                }
                repairTarget = repairTarget == WEST ? EAST : WEST;
                repairStrikeAt = now + REPAIR_STRIKE_TICKS;
                repairWarned = false;
                return result;
            }
            if (!repairWarned && now >= repairStrikeAt - REPAIR_WARNING_TICKS) {
                repairWarned = true;
                return RepairHazard.WARNING;
            }
            return RepairHazard.NONE;
        }

        Station nextRelease() {
            return switch (releaseStep) {
                case 0 -> westFirst ? Station.WEST_ROOM : Station.EAST_ROOM;
                case 1 -> westFirst ? Station.EAST_MARK : Station.WEST_MARK;
                case 2 -> westFirst ? Station.EAST_ROOM : Station.WEST_ROOM;
                case 3 -> westFirst ? Station.WEST_MARK : Station.EAST_MARK;
                default -> Station.DAIS;
            };
        }
    }

    private RouteFinaleRuntime() { }

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            Station station = station(world.getBlockState(hit.getBlockPos()).getBlock());
            if (station == null) return InteractionResult.PASS;
            if (!(world instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.SUCCESS;
            }
            return use(level, serverPlayer, hand, hit.getBlockPos(), station);
        });
        ServerTickEvents.END_SERVER_TICK.register(RouteFinaleRuntime::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(ACTIVE::remove);
    }

    private static Station station(Block block) {
        if (block == DomainBlocks.COURT_DAIS) return Station.DAIS;
        if (block == DomainBlocks.COURT_WEST_STATION) return Station.WEST_ROOM;
        if (block == DomainBlocks.COURT_EAST_STATION) return Station.EAST_ROOM;
        if (block == DomainBlocks.COURT_WEST_MARK) return Station.WEST_MARK;
        if (block == DomainBlocks.COURT_EAST_MARK) return Station.EAST_MARK;
        if (block == DomainBlocks.COURT_REMEMBER_ENDING || block == DomainBlocks.COURT_REPAIR_ENDING
                || block == DomainBlocks.COURT_RELEASE_ENDING) return Station.ENDING;
        return null;
    }

    public static boolean ready(MinecraftServer server, Path path) {
        if (path == null || (CampaignPersistentState.get(server).model().snapshot().worldChangedMask()
                & LandmarkKey.ALL_MASK) != LandmarkKey.ALL_MASK) return false;
        return switch (path) {
            case REMEMBER -> CampaignRouteState.get(server).rememberInkboundBound()
                    && CampaignRouteState.get(server).rememberArchiveCompared()
                    && CampaignRouteState.get(server).rememberTaleStage() == 5;
            case REPAIR -> RepairRouteState.get(server).soryalComplete()
                    && RepairRouteState.get(server).riverwardComplete()
                    && RepairRouteState.get(server).redlineComplete();
            case RELEASE -> ReleaseRouteState.get(server).nourRelayComplete()
                    && ReleaseRouteState.get(server).aliShortcutComplete()
                    && ReleaseRouteState.get(server).famDispatchComplete();
        };
    }

    private static InteractionResult use(ServerLevel level, ServerPlayer player, InteractionHand hand,
                                         BlockPos pos, Station station) {
        MinecraftServer server = level.getServer();
        Path path = CampaignRouteState.get(server).chosen();
        if (level.dimension() != Level.OVERWORLD || path == null) {
            tell(player, "Choose a world route at the townhall Story Council first.");
            return InteractionResult.SUCCESS_SERVER;
        }
        RouteFinaleState ending = RouteFinaleState.get(server);
        if (ending.complete()) {
            if (station == Station.DAIS) level.setBlock(pos, endingBlock(path).defaultBlockState(), Block.UPDATE_ALL);
            RouteEndingNarrative.open(player, path, OthmanArchiveState.get(server).published());
            return InteractionResult.SUCCESS_SERVER;
        }
        if (station == Station.ENDING) {
            // A block update can survive a crash while the world conclusion does not.
            // Restore the playable dais even if a route prerequisite was also lost.
            level.setBlock(pos, DomainBlocks.COURT_DAIS.defaultBlockState(), Block.UPDATE_ALL);
            ACTIVE.remove(server);
            tell(player, "The Court's ending was interrupted. Begin the final chapter again at this dais.");
            return InteractionResult.SUCCESS_SERVER;
        }
        if (station == Station.DAIS && isRealCourtDais(level, pos)) {
            EndSourceState.get(server).recordCourtReturn(player.getUUID(), pos);
        }
        if (!ready(server, path)) {
            tell(player, "Gather your three route leads and recover all seven boss identities first. "
                    + CampaignRouteRuntime.nextLead(server, path));
            return InteractionResult.SUCCESS_SERVER;
        }
        if (!RequiredChapters.readyForCourt(server, path)) {
            tell(player, RequiredChapters.nextLead(server, path));
            return InteractionResult.SUCCESS_SERVER;
        }
        long now = level.getGameTime();
        Attempt attempt = ACTIVE.get(server);
        if (station == Station.DAIS) {
            if (attempt != null && attempt.blocksOtherDais(pos, now)) {
                long dx = (long) attempt.dais.getX() - pos.getX();
                long dz = (long) attempt.dais.getZ() - pos.getZ();
                tell(player, "A chapter is underway at another Court "
                        + RumorAtlasItem.direction(dx, dz) + ", about "
                        + (int) Math.round(Math.hypot(dx, dz) / 10.0) * 10
                        + " blocks away. Join it or wait for it to end.");
                return InteractionResult.SUCCESS_SERVER;
            }
            if (attempt == null || attempt.path != path || !attempt.dais.equals(pos)
                    || now - attempt.lastAction > IDLE_TICKS
                    || (path == Path.RELEASE && attempt.releaseExpired(now))) {
                BlockPos west = find(level, pos, DomainBlocks.COURT_WEST_MARK);
                BlockPos east = find(level, pos, DomainBlocks.COURT_EAST_MARK);
                BlockPos westRoom = west == null || east == null ? null : sideRoom(pos, west, east, true);
                BlockPos eastRoom = west == null || east == null ? null : sideRoom(pos, west, east, false);
                if (west == null || east == null || westRoom == null || eastRoom == null) {
                    tell(player, "The Court's stage marks or side rooms are missing. This chapter cannot start here.");
                    return InteractionResult.SUCCESS_SERVER;
                }
                if (!level.getBlockState(westRoom).is(DomainBlocks.COURT_WEST_STATION)
                        || !level.getBlockState(eastRoom).is(DomainBlocks.COURT_EAST_STATION)) {
                    tell(player, "The Court's side rooms are missing. This chapter cannot start here.");
                    return InteractionResult.SUCCESS_SERVER;
                }
                attempt = new Attempt(path, pos, west, east, westRoom, eastRoom, now,
                        EndSourceState.get(server).cleared(path));
                ACTIVE.put(server, attempt);
                tell(player, startText(path, attempt));
                return InteractionResult.SUCCESS_SERVER;
            }
            if (path == Path.REPAIR && attempt.repairExpired(now)) {
                attempt.resetRepairHolds();
                tell(player, "The first repair line slipped before the other held. Secure both marks again within "
                        + attempt.repairStabilityLimit() / 20 + " seconds.");
                return InteractionResult.SUCCESS_SERVER;
            }
            attempt.lastAction = now;
            boolean done = switch (path) {
                case REMEMBER -> attempt.bound == (WEST | EAST);
                case REPAIR -> attempt.repairReady(now);
                case RELEASE -> attempt.releaseStep == 4 && !attempt.releaseExpired(now);
            };
            if (done) finish(level, player, pos, path);
            else tell(player, progressText(path, attempt, ending));
            return InteractionResult.SUCCESS_SERVER;
        }
        if (attempt == null || attempt.path != path || !attempt.owns(station, pos)
                || now - attempt.lastAction > IDLE_TICKS) {
            tell(player, "Start this chapter at the center of the Court's stage.");
            return InteractionResult.SUCCESS_SERVER;
        }
        if (path == Path.RELEASE && attempt.releaseExpired(now)) {
            ACTIVE.remove(server);
            tell(player, "The path changed before you finished. Start again at the center stage.");
            return InteractionResult.SUCCESS_SERVER;
        }
        if (path == Path.REPAIR && attempt.repairExpired(now)) {
            attempt.resetRepairHolds();
            tell(player, "The repair line slipped. Hold both supplied marks again within "
                    + attempt.repairStabilityLimit() / 20 + " seconds.");
        }
        attempt.lastAction = now;
        switch (path) {
            case REMEMBER -> remember(player, station, attempt, now);
            case REPAIR -> repair(level, player, hand, station, attempt, ending);
            case RELEASE -> release(player, station, attempt, now);
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    private static boolean isRealCourtDais(ServerLevel level, BlockPos pos) {
        Structure court = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                .getValue(COURT_STRUCTURE);
        if (court == null) return false;
        StructureStart start = level.structureManager().getStructureWithPieceAt(
                pos.getX(), pos.getY(), pos.getZ(), court);
        return start != null && start.isValid();
    }

    private static void remember(ServerPlayer player, Station station, Attempt attempt, long now) {
        switch (station) {
            case WEST_ROOM -> {
                attempt.westWitnessAt = now;
                tell(player, "The mask archive keeps a voice the script left out. Its trace lasts "
                        + attempt.witnessLimit() / 20 + " seconds.");
            }
            case EAST_ROOM -> {
                attempt.eastWitnessAt = now;
                tell(player, "The steward's account names the people who kept the stage running. Its trace lasts "
                        + attempt.witnessLimit() / 20 + " seconds.");
            }
            case WEST_MARK -> bindWitness(player, attempt, WEST, now);
            case EAST_MARK -> bindWitness(player, attempt, EAST, now);
            default -> { }
        }
    }

    private static void bindWitness(ServerPlayer player, Attempt attempt, int side, long now) {
        if ((attempt.bound & side) != 0) {
            tell(player, "You have already saved that side of the story.");
            return;
        }
        long foundAt = side == WEST ? attempt.westWitnessAt : attempt.eastWitnessAt;
        if (foundAt < 0 || now - foundAt > attempt.witnessLimit()) {
            tell(player, "That story has faded. Its side room still holds the account.");
            return;
        }
        attempt.bound |= side;
        tell(player, attempt.bound == (WEST | EAST)
                ? "Both sides of the story are safe. The center stage brightens."
                : "One voice is safe. The other side of the stage is still quiet.");
    }

    private static void repair(ServerLevel level, ServerPlayer player, InteractionHand hand,
                               Station station, Attempt attempt, RouteFinaleState state) {
        if (station == Station.WEST_ROOM || station == Station.EAST_ROOM) {
            int bit = station == Station.WEST_ROOM ? WEST : EAST;
            if (attempt.sourceAid && bit == WEST) {
                tell(player, "The End Source brace already holds the west work site. The east annex still needs glass.");
                return;
            }
            if ((attempt.effectiveRepairSupplies(state.repairSupplies()) & bit) != 0) {
                tell(player, "That work site has its materials. Its stage mark is still unsettled.");
                return;
            }
            Item needed = bit == WEST ? Items.COPPER_INGOT : Items.GLASS_PANE;
            ItemStack held = player.getItemInHand(hand);
            if (!player.getAbilities().instabuild && !held.is(needed)) {
                tell(player, bit == WEST ? "Bring one copper ingot to the mask archive work site."
                        : "Bring one glass pane to the steward's annex work site.");
                return;
            }
            if (state.deliverRepairSupply(bit)) {
                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                    player.getInventory().setChanged();
                }
                level.playSound(null, player.blockPosition(), ModSounds.ARCANE_CHIME,
                        SoundSource.BLOCKS, 0.55F, 0.8F);
                tell(player, "Materials are in place. The matching stage mark begins to glow.");
            }
            return;
        }
        if (station == Station.WEST_MARK || station == Station.EAST_MARK) {
            int bit = station == Station.WEST_MARK ? WEST : EAST;
            tell(player, (attempt.effectiveRepairSupplies(state.repairSupplies()) & bit) == 0
                    ? "That line needs materials from its side room first."
                    : (attempt.held & bit) != 0
                    ? "This side is safe. The other mark still flickers."
                    : "The repair crew needs four seconds of cover beside this mark.");
        }
    }

    private static void release(ServerPlayer player, Station station, Attempt attempt, long now) {
        Station expected = attempt.nextRelease();
        if (station != expected) {
            tell(player, "The lit path bends away from this mark. "
                    + Math.max(0, (attempt.releaseLimit() - (now - attempt.started)) / 20)
                    + " seconds remain.");
            return;
        }
        attempt.releaseStep++;
        tell(player, attempt.releaseStep == 4
                ? "The way is open. The center stage shines again."
                : "The call answered. The next light path has shifted.");
    }

    private static void tick(MinecraftServer server) {
        Attempt attempt = ACTIVE.get(server);
        if (attempt == null || server.getTickCount() % 10 != 0) return;
        ServerLevel level = server.getLevel(Level.OVERWORLD);
        if (level == null || RouteFinaleState.get(server).complete()
                || level.getGameTime() - attempt.lastAction > IDLE_TICKS) {
            ACTIVE.remove(server);
            return;
        }
        if (attempt.path != Path.REPAIR) return;
        if (level.players().stream().noneMatch(player -> player.isAlive() && !player.isSpectator()
                && attempt.nearby(player.blockPosition()))) {
            ACTIVE.remove(server);
            return;
        }
        if (attempt.repairExpired(level.getGameTime())) {
            attempt.resetRepairHolds();
            announce(level, "The Court's repair line slipped. Hold both supplied marks again.");
        }
        int supplies = attempt.effectiveRepairSupplies(RouteFinaleState.get(server).repairSupplies());
        long now = level.getGameTime();
        int target = attempt.repairTarget;
        BlockPos threatened = target == WEST ? attempt.westMark : attempt.eastMark;
        boolean strikeDue = attempt.repairStrikeAt >= 0 && now >= attempt.repairStrikeAt;
        RepairHazard hazard = attempt.advanceRepairHazard(now, supplies,
                strikeDue && guarded(level, threatened));
        if (hazard == RepairHazard.WARNING) {
            announceNearby(level, attempt, (target == WEST ? "West" : "East")
                    + " work site is under threat. Stand at its stage mark to shield the crew in four seconds.");
            level.playSound(null, threatened, AdventureSounds.CADENZA_CALL, SoundSource.BLOCKS, 0.8F, 1.1F);
        } else if (hazard == RepairHazard.GUARDED) {
            announceNearby(level, attempt, "The crew is safe. The strike was blocked at the "
                    + (target == WEST ? "west" : "east") + " mark.");
            level.playSound(null, threatened, ModSounds.ARCANE_CHIME, SoundSource.BLOCKS, 0.7F, 1.2F);
        } else if (hazard == RepairHazard.BREACHED) {
            announceNearby(level, attempt, "The " + (target == WEST ? "west" : "east")
                    + " work site was hit. Hold that mark again and guard the next strike.");
            level.playSound(null, threatened, AdventureSounds.CADENZA_STRIKE, SoundSource.BLOCKS, 0.8F, 0.85F);
        }
        if (attempt.repairWarned && supplies == (WEST | EAST)) {
            BlockPos warningMark = attempt.repairTarget == WEST ? attempt.westMark : attempt.eastMark;
            level.sendParticles(ModParticles.GEM_RING, warningMark.getX() + 0.5,
                    warningMark.getY() + 1.2, warningMark.getZ() + 0.5,
                    6, 0.35, 0.1, 0.35, 0.02);
        }
        attempt.westHoldTicks = hold(level, attempt.westMark, supplies, attempt.held,
                WEST, attempt.westHoldTicks);
        attempt.eastHoldTicks = hold(level, attempt.eastMark, supplies, attempt.held,
                EAST, attempt.eastHoldTicks);
        if (attempt.westHoldTicks >= 80 && (attempt.held & WEST) == 0) {
            attempt.held |= WEST;
            if (attempt.firstRepairHoldAt < 0) attempt.firstRepairHoldAt = level.getGameTime();
            announce(level, "The west repair line is holding.");
        }
        if (attempt.eastHoldTicks >= 80 && (attempt.held & EAST) == 0) {
            attempt.held |= EAST;
            if (attempt.firstRepairHoldAt < 0) attempt.firstRepairHoldAt = level.getGameTime();
            announce(level, "The east repair line is holding.");
        }
        if (attempt.held == (WEST | EAST) && !attempt.readyAnnounced) {
            attempt.readyAnnounced = true;
            announce(level, "Both routes are safe. Return to the Court's dais to finish the repair.");
        }
    }

    private static int hold(ServerLevel level, BlockPos mark, int supplies, int held,
                            int bit, int ticks) {
        if ((supplies & bit) == 0 || (held & bit) != 0) return ticks;
        return guarded(level, mark) ? ticks + 10 : 0;
    }

    private static boolean guarded(ServerLevel level, BlockPos mark) {
        return level.players().stream().anyMatch(player -> player.isAlive() && !player.isSpectator()
                && player.distanceToSqr(mark.getX() + 0.5, mark.getY() + 0.5,
                        mark.getZ() + 0.5) <= 3.5 * 3.5);
    }

    /** The two rigid side pieces attach at fixed offsets from the root's stage-mark axes. */
    static BlockPos sideRoom(BlockPos dais, BlockPos westMark, BlockPos eastMark, boolean west) {
        int eastDx = eastMark.getX() - westMark.getX();
        int eastDz = eastMark.getZ() - westMark.getZ();
        int northDx = westMark.getX() + eastMark.getX() - 2 * dais.getX();
        int northDz = westMark.getZ() + eastMark.getZ() - 2 * dais.getZ();
        if ((Math.abs(eastDx) + Math.abs(eastDz) != 10)
                || (Math.abs(northDx) + Math.abs(northDz) != 18)) return null;
        int lateral = west ? -34 : 35;
        return dais.offset(lateral * eastDx / 10 + 7 * northDx / 18,
                0, lateral * eastDz / 10 + 7 * northDz / 18);
    }

    private static BlockPos find(ServerLevel level, BlockPos around, Block block) {
        for (int x = -15; x <= 15; x++) {
            for (int z = -15; z <= 15; z++) {
                BlockPos candidate = around.offset(x, 0, z);
                if (level.getBlockState(candidate).is(block)) return candidate;
            }
        }
        return null;
    }

    private static void finish(ServerLevel level, ServerPlayer player, BlockPos dais, Path path) {
        RouteFinaleState state = RouteFinaleState.get(level.getServer());
        if (!level.getBlockState(dais).is(endingBlock(path))
                && !level.setBlock(dais, endingBlock(path).defaultBlockState(), Block.UPDATE_ALL)) {
            tell(player, "The stage did not change. Try the dais again.");
            return;
        }
        if (!state.finish()) return;
        ACTIVE.remove(level.getServer());
        level.playSound(null, dais, ModSounds.ARCANE_CHIME, SoundSource.BLOCKS, 1.0F, 0.75F);
        announce(level, endingText(path));
        int publishedLead = OthmanArchiveState.get(level.getServer()).published();
        for (ServerPlayer witness : level.players()) {
            if (witness.blockPosition().distSqr(dais) <= 128 * 128) {
                RouteEndingNarrative.open(witness, path, publishedLead);
            }
        }
    }

    private static Block endingBlock(Path path) {
        return switch (path) {
            case REMEMBER -> DomainBlocks.COURT_REMEMBER_ENDING;
            case REPAIR -> DomainBlocks.COURT_REPAIR_ENDING;
            case RELEASE -> DomainBlocks.COURT_RELEASE_ENDING;
        };
    }

    private static String startText(Path path, Attempt attempt) {
        return switch (path) {
            case REMEMBER -> attempt.sourceAid
                    ? "Remember: the west story is safe. The east room still has a voice."
                    : "Remember: the two side rooms hold voices the script left out. Their traces fade quickly.";
            case REPAIR -> "Repair: " + (attempt.sourceAid
                    ? "the source braces the west work site; the east annex is damaged."
                    : "the side rooms show different damage.")
                    + " The crew has " + attempt.repairStabilityLimit() / 20
                    + " seconds of stability after the first mark holds.";
            case RELEASE -> "Release: " + (attempt.sourceAid
                    ? "the first call is already done. "
                    : "the path changes after each call. ")
                    + "The stage lights will hold for " + attempt.releaseLimit() / 20 + " seconds.";
        };
    }

    private static String progressText(Path path, Attempt attempt, RouteFinaleState state) {
        return switch (path) {
            case REMEMBER -> "Stories saved: " + Integer.bitCount(attempt.bound) + "/2."
                    + (attempt.sourceAid ? " The east room still has a voice."
                    : " An unsaved voice still lingers in a side room.");
            case REPAIR -> "Repair line: " + Integer.bitCount(attempt.effectiveRepairSupplies(state.repairSupplies()))
                    + "/2 materials delivered, " + Integer.bitCount(attempt.held)
                    + "/2 marks held, crew " + (attempt.defendedStrike ? "shielded" : "unshielded")
                    + ". The stage marks and a called strike still threaten the crew; stability lasts "
                    + attempt.repairStabilityLimit() / 20 + " seconds after the first hold.";
            case RELEASE -> "Changing path: " + attempt.releaseStep + "/4 calls"
                    + (attempt.sourceAid ? " (first carried by the source)" : "")
                    + ". The next stage light has moved.";
        };
    }

    private static String endingText(Path path) {
        return switch (path) {
            case REMEMBER -> "Remember is complete. The Court keeps both witnesses. Read the epilogue at the Court or townhall council.";
            case REPAIR -> "Repair is complete. The Court's work line stays open. Read the epilogue at the Court or townhall council.";
            case RELEASE -> "Release is complete. The Court accepted the last signal. Read the epilogue at the Court or townhall council.";
        };
    }

    private static void announce(ServerLevel level, String message) {
        level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(message), false);
    }

    private static void announceNearby(ServerLevel level, Attempt attempt, String message) {
        level.players().stream().filter(player -> attempt.nearby(player.blockPosition()))
                .forEach(player -> tell(player, message));
    }

    private static void tell(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message), false);
    }
}
