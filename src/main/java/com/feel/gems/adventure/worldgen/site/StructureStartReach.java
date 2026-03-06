package com.feel.gems.adventure.worldgen.site;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pools.EmptyPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.LegacySinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * Conservative start-chunk search reach for the 26.3 structure generators.
 * A missing bound is a failed reservation, including for a registered custom
 * generator whose templates or post-processing have not been audited yet.
 */
final class StructureStartReach {
    private static final String VANILLA =
            "net.minecraft.world.level.levelgen.structure.structures.";
    private static final int MAX_REACH_CHUNKS = 40;

    private StructureStartReach() { }

    /** One survey's runtime-resource validation, discarded after that site. */
    static final class Session {
        private final Map<Structure, Integer> reaches = new IdentityHashMap<>();
        private final Map<String, Boolean> templateBounds = new HashMap<>();
        private List<Identifier> templateIds;

        int chunks(Structure structure, StructureTemplateManager templates,
                   RegistryAccess registries) {
            Integer known = reaches.get(structure);
            if (known != null) return known;
            int bound = computeChunks(structure, templates, registries, this);
            reaches.put(structure, bound);
            return bound;
        }

        private boolean templatesFit(StructureTemplateManager templates,
                                     String prefix, int maxSpan) {
            String key = prefix + ":" + maxSpan;
            Boolean known = templateBounds.get(key);
            if (known != null) return known;
            if (templateIds == null) templateIds = templates.listTemplates().toList();
            boolean found = false, valid = true;
            for (Identifier id : templateIds) {
                if (!id.getNamespace().equals("minecraft")
                        || !id.getPath().startsWith(prefix)) continue;
                found = true;
                var size = templates.getOrCreate(id).getSize();
                if (size.getX() < 1 || size.getZ() < 1
                        || size.getX() > maxSpan || size.getZ() > maxSpan) {
                    valid = false;
                    break;
                }
            }
            boolean result = found && valid;
            templateBounds.put(key, result);
            return result;
        }
    }

    private static int computeChunks(Structure structure,
            StructureTemplateManager templates, RegistryAccess registries,
            Session session) {
        Identifier type = BuiltInRegistries.STRUCTURE_TYPE.getKey(structure.type());
        if (type == null) return -1;
        String name = type.toString();
        if (name.equals("minecraft:jigsaw")) {
            if (structure.getClass() != JigsawStructure.class) return -1;
            int body = jigsaw((JigsawStructure) structure, templates, registries);
            return body < 1 ? -1 : body + 1;
        }
        String className = structure.getClass().getName();
        int reach = switch (name) {
            // Child origins are checked against the initial piece at 80, 112,
            // and 112 blocks respectively. These margins include child width
            // and the start position within its candidate chunk.
            case "minecraft:mineshaft" -> vanilla(className, "MineshaftStructure", 12);
            case "minecraft:stronghold" -> vanilla(className, "StrongholdStructure", 12);
            case "minecraft:fortress" -> vanilla(className, "NetherFortressStructure", 12);

            // An 11x11 mansion layout of eight-block cells is under 128
            // blocks across, including the packaged 21-block templates.
            case "minecraft:woodland_mansion" -> vanillaTemplate(className,
                    "WoodlandMansionStructure", 16, templates, "woodland_mansion/", 64,
                    session);

            // End city recursion stops at depth 8. The bridge generator adds
            // at most five short segments per level; the ship's ~70-block
            // lateral offset is terminal. The 40-chunk envelope includes
            // connector rotations, the 29-block ship and chunk alignment.
            case "minecraft:end_city" -> vanillaTemplate(className,
                    "EndCityStructure", 40, templates, "end_city/", 29, session);

            // Ocean-ruin cluster offsets and the portal's extra +/-14-block
            // netherrack spread are included beyond their template boxes.
            case "minecraft:ocean_ruin" -> vanillaTemplate(className,
                    "OceanRuinStructure", 8, templates, "underwater_ruin/", 64, session);
            case "minecraft:ruined_portal" -> vanillaTemplate(className,
                    "RuinedPortalStructure", 8, templates, "ruined_portal/", 64, session);

            // Compact fixed layouts. The igloo basement and monument's
            // internal pieces stay in their parent's horizontal footprint.
            // Margins exceed the largest packaged spans (shipwreck 28,
            // monument 58, and the other listed pieces below 32).
            case "minecraft:buried_treasure" -> vanilla(className, "BuriedTreasureStructure", 8);
            case "minecraft:desert_pyramid" -> vanilla(className, "DesertPyramidStructure", 8);
            case "minecraft:igloo" -> vanillaTemplate(className,
                    "IglooStructure", 8, templates, "igloo/", 64, session);
            case "minecraft:jungle_temple" -> vanilla(className, "JungleTempleStructure", 8);
            case "minecraft:nether_fossil" -> vanillaTemplate(className,
                    "NetherFossilStructure", 8, templates, "fossil/", 64, session);
            case "minecraft:ocean_monument" -> vanilla(className, "OceanMonumentStructure", 8);
            case "minecraft:shipwreck" -> vanillaTemplate(className,
                    "ShipwreckStructure", 8, templates, "shipwreck/", 64, session);
            case "minecraft:swamp_hut" -> vanilla(className, "SwampHutStructure", 8);

            // Every audited root is checked at all four rotations below.
            // Unknown wrappers and datapack elements still have no bound.
            default -> auditedCustom(name, structure, templates, registries);
        };
        return reach > 0 && reach <= MAX_REACH_CHUNKS ? reach : -1;
    }

    private static int auditedCustom(String type, Structure structure,
                                     StructureTemplateManager templates,
                                     RegistryAccess registries) {
        AuditedJigsawTypes.Entry audit = AuditedJigsawTypes.get(type);
        return audit == null ? -1
                : custom(structure, audit.exactClass(), templates, registries);
    }

    private static int vanilla(String actual, String simple, int reach) {
        return actual.equals(VANILLA + simple) ? reach : -1;
    }

    private static int custom(Structure structure, Class<? extends Structure> exact,
                              StructureTemplateManager templates,
                              RegistryAccess registries) {
        if (structure.getClass() != exact
                || !(structure instanceof BoundedJigsawStart bounded)
                || bounded.reservationDelegate() == null) return -1;
        int extra = bounded.reservationExtraReachBlocks();
        if (extra < 0 || extra > 32) return -1;
        int shifted = bounded.reservationMaximumHorizontalShiftBlocks();
        if (shifted < 0 || shifted > 64) return -1;
        int body = jigsaw(bounded.reservationDelegate(), templates, registries);
        // The original piece can process into any column of an intersecting
        // chunk, at most fifteen blocks beyond its own X/Z box. Use whichever
        // extends farther: that chunk apron or the site's outside arrival.
        return body < 1 ? -1 : body
                + (shifted + Math.max(15, extra) + 15) / 16;
    }

    private static int vanillaTemplate(String actual, String simple, int reach,
            StructureTemplateManager templates, String prefix, int maxSpan,
            Session session) {
        if (!actual.equals(VANILLA + simple)) return -1;
        return session.templatesFit(templates, prefix, maxSpan) ? reach : -1;
    }

    private static int jigsaw(JigsawStructure structure,
                              StructureTemplateManager templates,
                              RegistryAccess registries) {
        int maxRootOffset = poolOffset(structure.getStartPool().value(), templates);
        if (maxRootOffset < 0) return -1;
        // Pool aliases can replace the declared start pool before placement.
        // Validate every possible target, including branches of random-group
        // aliases, even if most targets are only used for later children. This
        // is conservative and keeps datapack substitutions in the same bound.
        var pools = registries.lookupOrThrow(Registries.TEMPLATE_POOL);
        for (var alias : structure.getPoolAliases()) {
            for (var target : alias.allTargets().distinct().toList()) {
                StructureTemplatePool pool = pools.getValue(target);
                if (pool == null) return -1;
                int offset = poolOffset(pool, templates);
                if (offset < 0) return -1;
                maxRootOffset = Math.max(maxRootOffset, offset);
            }
        }
        // The encoded runtime value bounds JigsawPlacement's child enclosure
        // from the first-piece center. Decode the actual registered structure
        // so datapack overrides retain their own conservative search reach.
        // If encoding is uncertain, retain the codec's full 128-block cap.
        int distance = horizontalDistance(structure, registries);
        if (distance < 1) return -1;
        // Two root offsets cover a named internal start connector and the
        // first-piece center; 16 covers the candidate chunk origin.
        return (distance + 2 * maxRootOffset + 16 + 15) / 16;
    }

    private static int horizontalDistance(JigsawStructure structure,
                                          RegistryAccess registries) {
        JsonElement encoded = JigsawStructure.CODEC.codec().encodeStart(
                RegistryOps.create(JsonOps.INSTANCE, registries), structure)
                .result().orElse(null);
        if (!(encoded instanceof JsonObject object)) return 128;
        JsonElement distance = object.get("max_distance_from_center");
        if (distance instanceof JsonObject dimensions)
            distance = dimensions.get("horizontal");
        if (!(distance instanceof JsonPrimitive number) || !number.isNumber())
            return 128;
        try {
            int value = number.getAsInt();
            return value >= 1 && value <= 128 ? value : -1;
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private static int poolOffset(StructureTemplatePool pool,
                                  StructureTemplateManager templates) {
        int maxRootOffset = 0;
        var roots = pool.getTemplates();
        if (roots.isEmpty()) return -1;
        for (var weighted : roots) {
            if (weighted.getSecond() <= 0) return -1;
            var element = weighted.getFirst();
            Class<?> kind = element.getClass();
            if (kind == EmptyPoolElement.class) continue;
            if (kind != SinglePoolElement.class
                    && kind != LegacySinglePoolElement.class) return -1;
            for (Rotation rotation : Rotation.values()) {
                BoundingBox box = element.getBoundingBox(templates, BlockPos.ZERO, rotation);
                if (box == null) return -1;
                long extreme = Math.max(Math.max(Math.abs((long) box.minX()),
                        Math.abs((long) box.maxX())),
                        Math.max(Math.abs((long) box.minZ()),
                                Math.abs((long) box.maxZ())));
                if (extreme > 128) return -1;
                maxRootOffset = Math.max(maxRootOffset, (int) extreme);
            }
        }
        return maxRootOffset;
    }
}
