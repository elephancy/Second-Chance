package com.feel.gems.adventure.worldgen;

import com.feel.gems.GemsMod;
import com.feel.gems.adventure.DomainBlocks;
import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/** Finishes new district arrivals only where their whole walking route is open. */
public final class DistrictArrivalAccessFeature implements Feature {
    public static final MapCodec<DistrictArrivalAccessFeature> CODEC =
            MapCodec.unit(new DistrictArrivalAccessFeature());
    private static final Identifier ID = Identifier.fromNamespaceAndPath(GemsMod.MOD_ID,
            "district_arrival_access");
    private static final ResourceKey<PlacedFeature> PLACED =
            ResourceKey.create(Registries.PLACED_FEATURE, ID);
    private static final ResourceKey<Structure> ALI = ResourceKey.create(
            Registries.STRUCTURE,
            Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, "ali_splitstone_cloister"));

    private DistrictArrivalAccessFeature() { }

    public static void init() {
        Registry.register(BuiltInRegistries.FEATURE_TYPE, ID, CODEC);
        BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(),
                GenerationStep.Decoration.TOP_LAYER_MODIFICATION, PLACED);
    }

    @Override
    public MapCodec<DistrictArrivalAccessFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random,
                         BlockPos origin) {
        if (!(level instanceof WorldGenRegion region)) return false;
        ChunkPos center = region.getCenter();
        Structure ali = region.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                .getValue(ALI);
        boolean placed = ali != null && AliArrivalTransition.clearCenterChunkCanopy(
                region, center, aliStart(region, center, ali));
        // The Wadi marker and the low end of its stair can be in adjacent
        // chunks. Discover the marker in the region, but write only center.
        for (int cz = center.z() - 1; cz <= center.z() + 1; cz++) {
            for (int cx = center.x() - 1; cx <= center.x() + 1; cx++) {
                if (region.hasChunk(cx, cz)) {
                    placed |= visitMarkers(region, region.getChunk(cx, cz),
                            new ChunkPos(cx, cz));
                }
            }
        }
        return placed;
    }

    private static StructureStart aliStart(WorldGenRegion region, ChunkPos center,
            Structure ali) {
        StructureStart match = null;
        for (long reference : region.getChunk(center.x(), center.z(),
                ChunkStatus.STRUCTURE_REFERENCES, false)
                .getReferencesForStructure(ali)) {
            int startX = ChunkPos.getX(reference);
            int startZ = ChunkPos.getZ(reference);
            if (Math.abs(startX - center.x()) > 8
                    || Math.abs(startZ - center.z()) > 8
                    || !region.hasChunk(startX, startZ)) return null;
            StructureStart start = region.getChunk(startX, startZ,
                    ChunkStatus.STRUCTURE_STARTS, false)
                    .getStartForStructure(ali);
            if (start == null || !start.isValid()
                    || !AliDryArrivalStructure.hasCurrentV5Carrier(start))
                continue;
            if (match != null) return null;
            match = start;
        }
        return match;
    }

    private static boolean visitMarkers(WorldGenRegion region, ChunkAccess chunk,
                                        ChunkPos markerChunk) {
        LevelChunkSection[] sections = chunk.getSections();
        boolean placed = false;
        for (int index = 0; index < sections.length; index++) {
            LevelChunkSection section = sections[index];
            if (!section.maybeHas(state -> state.is(Blocks.OBSERVER))) continue;
            int y0 = region.getMinY() + index * 16;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (!state.is(Blocks.OBSERVER)) continue;
                        BlockPos marker = new BlockPos(markerChunk.getMinBlockX() + x,
                                y0 + y, markerChunk.getMinBlockZ() + z);
                        Direction outward = state.getValue(ObserverBlock.FACING);
                        if (!outward.getAxis().isHorizontal()) continue;
                        if (region.getBlockState(marker.above()).is(DomainBlocks.LOST_CLAY)
                                && region.getBlockState(marker.above(3))
                                        .is(DomainBlocks.LOST_TILE)) {
                            placed |= wadiStair(region, marker, outward);
                        } else if (region.getBlockState(marker.above())
                                .is(DomainBlocks.RIVERWARD_LIMESTONE)
                                && region.getBlockState(marker.above(2))
                                        .is(Blocks.POLISHED_TUFF)) {
                            placed |= clinicBridge(region, marker, outward);
                        }
                    }
                }
            }
        }
        return placed;
    }

    private static boolean clinicBridge(WorldGenRegion region, BlockPos marker,
                                        Direction outward) {
        int baseY = marker.getY() - 2;
        BlockPos oldStair = marker.relative(outward);
        if (!withinReach(region, oldStair)) return false;
        if (!region.getBlockState(oldStair.atY(baseY + 3))
                .is(Blocks.STONE_BRICK_STAIRS)) return false;
        List<Placement> writes = new ArrayList<>();
        int waterColumns = 0;
        int dryExits = 0;
        for (int distance = 2; distance <= 14; distance++) {
            int floorY = baseY + (distance <= 3 ? 3 : distance <= 7 ? 2 : 1);
            for (int side = -1; side <= 1; side++) {
                BlockPos floor = transverse(marker.relative(outward, distance),
                        outward, side).atY(floorY);
                BlockState floorState = distance % 4 == 0
                        ? DomainBlocks.RIVERWARD_LIMESTONE.defaultBlockState()
                        : Blocks.POLISHED_TUFF.defaultBlockState();
                if (!open(region, floor, 4, floorState)) return false;
                writes.add(new Placement(floor, distance % 4 == 0
                        ? DomainBlocks.RIVERWARD_LIMESTONE.defaultBlockState()
                        : Blocks.POLISHED_TUFF.defaultBlockState()));
                if (side == 0 && distance > 2 && distance < 14
                        && !region.getBlockState(floor.atY(baseY))
                                .getFluidState().isEmpty()) waterColumns++;
                if (distance == 14) {
                    BlockState bank = region.getBlockState(floor.below());
                    BlockPos beyondPos = floor.relative(outward).below();
                    if (!withinReach(region, beyondPos)) return false;
                    BlockState beyond = region.getBlockState(beyondPos);
                    if (!drySupport(bank)) return false;
                    if (drySupport(beyond)) dryExits++;
                    if (side == 0 && !drySupport(beyond)) return false;
                }
            }
            for (int side : new int[] {-2, 2}) {
                BlockPos rail = transverse(marker.relative(outward, distance),
                        outward, side).atY(floorY);
                BlockState railState = DomainBlocks.RIVERWARD_LIMESTONE.defaultBlockState();
                BlockState topState = distance % 4 == 0
                        ? Blocks.SEA_LANTERN.defaultBlockState()
                        : Blocks.STONE_BRICK_WALL.defaultBlockState();
                if (!open(region, rail, 2, railState, topState)) return false;
                writes.add(new Placement(rail,
                        DomainBlocks.RIVERWARD_LIMESTONE.defaultBlockState()));
                writes.add(new Placement(rail.above(), distance % 4 == 0
                        ? Blocks.SEA_LANTERN.defaultBlockState()
                        : Blocks.STONE_BRICK_WALL.defaultBlockState()));
            }
        }
        if (waterColumns < 5 || dryExits < 2) return false;
        return commit(region, writes);
    }

    private static boolean wadiStair(WorldGenRegion region, BlockPos marker,
                                     Direction outward) {
        int baseY = marker.getY() - 1;
        if (!region.getBlockState(marker.above(3)).is(DomainBlocks.LOST_TILE)) return false;
        List<Placement> writes = new ArrayList<>();
        for (int distance = 1; distance <= 13; distance++) {
            int floorY = baseY + 5 - distance;
            int firstSide = distance <= 7 ? -1 : distance == 8 ? 0
                    : distance <= 12 ? 1 : 0;
            for (int side = firstSide; side <= firstSide + 1; side++) {
                BlockPos floor = transverse(marker.relative(outward, distance),
                        outward, side).atY(floorY);
                if (!open(region, floor, 3,
                        DomainBlocks.LOST_TILE.defaultBlockState())) return false;
                writes.add(new Placement(floor, DomainBlocks.LOST_TILE.defaultBlockState()));
            }
            // The stair turns around the acacia canopy. A rail that meets a
            // leaf is omitted; the tree itself remains intact.
            for (int side : new int[] {firstSide - 1, firstSide + 2}) {
                if (distance == 13 && side == firstSide + 2) continue;
                BlockPos rail = transverse(marker.relative(outward, distance),
                        outward, side).atY(floorY);
                BlockState topState = distance % 4 == 0
                        ? Blocks.SEA_LANTERN.defaultBlockState()
                        : Blocks.SANDSTONE_WALL.defaultBlockState();
                if (!open(region, rail, 2,
                        Blocks.CUT_SANDSTONE.defaultBlockState(), topState)) continue;
                writes.add(new Placement(rail, Blocks.CUT_SANDSTONE.defaultBlockState()));
                writes.add(new Placement(rail.above(), distance % 4 == 0
                        ? Blocks.SEA_LANTERN.defaultBlockState()
                        : Blocks.SANDSTONE_WALL.defaultBlockState()));
            }
        }
        // A dry side step enters the low end without touching its short grass.
        BlockPos landing = transverse(marker.relative(outward, 13), outward, 2)
                .atY(baseY - 9);
        if (!withinReach(region, landing) || !drySupport(region.getBlockState(landing))
                || !region.getBlockState(landing.above()).getFluidState().isEmpty()
                || !region.getBlockState(landing.above())
                        .getCollisionShape(region, landing.above()).isEmpty()) return false;
        return commit(region, writes);
    }

    private static BlockPos transverse(BlockPos pos, Direction outward, int side) {
        return pos.offset(-outward.getStepZ() * side, 0,
                outward.getStepX() * side);
    }

    private static boolean drySupport(BlockState state) {
        return state.isSolid() && state.getFluidState().isEmpty();
    }

    private static boolean open(WorldGenRegion region, BlockPos floor, int height,
                                BlockState... authored) {
        for (int rise = 0; rise < height; rise++) {
            BlockPos pos = floor.above(rise);
            if (!withinReach(region, pos)) return false;
            BlockState state = region.getBlockState(pos);
            if (!state.isAir() && (rise >= authored.length
                    || !state.equals(authored[rise]))) return false;
        }
        return true;
    }

    private static boolean withinReach(WorldGenRegion region, BlockPos pos) {
        ChunkPos center = region.getCenter();
        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;
        return withinReach(center, pos)
                && region.hasChunk(cx, cz);
    }

    static boolean withinReach(ChunkPos center, BlockPos pos) {
        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;
        return Math.abs(cx - center.x()) <= 1 && Math.abs(cz - center.z()) <= 1;
    }

    private static boolean commit(WorldGenRegion region, List<Placement> writes) {
        ChunkPos center = region.getCenter();
        List<Placement> local = writes.stream().filter(write ->
                (write.pos().getX() >> 4) == center.x()
                        && (write.pos().getZ() >> 4) == center.z()).toList();
        if (local.isEmpty() || local.stream().anyMatch(write ->
                !region.ensureCanWrite(write.pos())
                        || !(region.getBlockState(write.pos()).isAir()
                        || region.getBlockState(write.pos()).equals(write.state())))) return false;
        for (Placement write : local) {
            if (region.getBlockState(write.pos()).isAir()) {
                region.setBlock(write.pos(), write.state(), 2);
            }
        }
        return true;
    }

    private record Placement(BlockPos pos, BlockState state) { }
}
