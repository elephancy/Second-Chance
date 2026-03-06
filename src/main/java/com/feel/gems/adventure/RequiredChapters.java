package com.feel.gems.adventure;

import com.feel.gems.adventure.CampaignRouteState.Path;
import com.feel.gems.adventure.endsource.EndSourceState;
import com.feel.gems.adventure.nether.NetherChapterState;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.StructureSet;

/** Enables the full dimension chapter route only with all three natural starts. */
public final class RequiredChapters {
    enum Activation { INCOMPLETE, ACTIVE }
    private static final ResourceKey<StructureSet> ASH = key("lastlight_ash_annex");
    private static final ResourceKey<StructureSet> EMBER = key("redline_ember_bridge");
    private static final ResourceKey<StructureSet> SOURCE = key("unwritten_end_source");

    private RequiredChapters() { }

    private static ResourceKey<StructureSet> key(String path) {
        return ResourceKey.create(Registries.STRUCTURE_SET,
                Identifier.fromNamespaceAndPath("gems", path));
    }

    private static Activation activation(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return Activation.INCOMPLETE;
        var sets = overworld.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET);
        return activation(sets.getValue(ASH) != null, sets.getValue(EMBER) != null,
                sets.getValue(SOURCE) != null);
    }

    static Activation activation(boolean ash, boolean ember, boolean source) {
        int count = (ash ? 1 : 0) + (ember ? 1 : 0) + (source ? 1 : 0);
        return count == 3 ? Activation.ACTIVE : Activation.INCOMPLETE;
    }

    /** All three natural structure sets are one release activation unit. */
    public static boolean active(MinecraftServer server) {
        return activation(server) == Activation.ACTIVE;
    }

    public static boolean readyForCourt(MinecraftServer server, Path path) {
        Activation activation = activation(server);
        if (activation == Activation.INCOMPLETE) return false;
        if (!NetherChapterState.get(server).requiredChaptersCleared()) return false;
        return EndSourceState.get(server).cleared(path);
    }

    public static String nextLead(MinecraftServer server, Path path) {
        Activation activation = activation(server);
        if (activation == Activation.INCOMPLETE) {
            return "This world's Ash Annex, Ember Bridge and End Source chapters are incomplete. "
                    + "Ask the server owner to restore the full chapter data pack before starting the Court.";
        }
        if (path == null) return null;
        return nextLead(path, NetherChapterState.get(server), EndSourceState.get(server));
    }

    /** Describe durable world progress so a player joining after a restart can pick up the route. */
    static String nextLead(Path path, NetherChapterState nether, EndSourceState source) {
        if (path == null) return null;
        if (!nether.annexCleared()) {
            return nether.allRecordsRead()
                    ? "All three Ash Annex records are found. Its departure board has changed."
                    : "Ash Annex stands in the Nether wastes. The Rumor Atlas marks its depot. Clues found: "
                    + nether.recordCount() + "/3. The interrupted departure left traces across the depot.";
        }
        if (!nether.bridgeCleared()) {
            return nether.bothSheltersRestored()
                    ? "Both Ember Bridge shelters are restored. The watch line is ready for an evacuation."
                    : "Ember Bridge stands in a Nether warped forest. The Rumor Atlas marks it. Shared shelters: "
                    + nether.shelterCount() + "/2. The old masonry and metalwork show what is missing.";
        }
        if (!source.cleared(path)) {
            String objective = switch (path) {
                case REMEMBER -> "The side galleries hold two different accounts.";
                case REPAIR -> "The side lenses pulse against the damage around them.";
                case RELEASE -> "The gallery calls move with the light.";
            };
            return "Beyond the End gateway, the Rumor Atlas marks an outer-island observatory. "
                    + objective + " Its return arch remains open; the Court waits afterward.";
        }
        return null;
    }

    /** A completed ending can still show the state of the required chapters. */
    public static String postgameLead(MinecraftServer server, Path path) {
        if (!active(server) || path == null) return null;
        String lead = nextLead(server, path);
        return lead == null ? "The Ash Annex, Ember Bridge and End Source are all restored."
                : lead;
    }
}
