package com.feel.gems.adventure.nether;

import com.feel.gems.GemsMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** World-shared chapter progress, separate from the seven stable boss bits. */
public final class NetherChapterState extends SavedData {
    public static final int REGISTER = 1;
    public static final int SIGNAL = 2;
    public static final int PLATFORM = 4;
    public static final int EAST_SHELTER = 1;
    public static final int WEST_SHELTER = 2;
    private static final int ALL_RECORDS = REGISTER | SIGNAL | PLATFORM;
    private static final int BOTH_SHELTERS = EAST_SHELTER | WEST_SHELTER;

    static final Codec<NetherChapterState> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.optionalFieldOf("version", 1).forGetter(state -> 1),
                    Codec.INT.optionalFieldOf("annexRecords", 0).forGetter(state -> state.annexRecords),
                    Codec.BOOL.optionalFieldOf("annexCleared", false).forGetter(state -> state.annexCleared),
                    Codec.INT.optionalFieldOf("bridgeShelters", 0).forGetter(state -> state.bridgeShelters),
                    Codec.BOOL.optionalFieldOf("bridgeCleared", false).forGetter(state -> state.bridgeCleared)
            ).apply(instance, NetherChapterState::new));
    private static final SavedDataType<NetherChapterState> TYPE = new SavedDataType<>(
            Identifier.withDefaultNamespace(GemsMod.MOD_ID + "_nether_chapters_v1"),
            NetherChapterState::new, CODEC, DataFixTypes.SAVED_DATA_MAP_DATA);

    private int annexRecords;
    private boolean annexCleared;
    private int bridgeShelters;
    private boolean bridgeCleared;

    public NetherChapterState() {
        this(1, 0, false, 0, false);
    }

    private NetherChapterState(int version, int records, boolean annex,
                               int shelters, boolean bridge) {
        if (version != 1) throw new IllegalArgumentException("Unknown Nether chapter save version");
        annexRecords = records & ALL_RECORDS;
        annexCleared = annex && annexRecords == ALL_RECORDS;
        bridgeShelters = shelters & BOTH_SHELTERS;
        bridgeCleared = bridge && bridgeShelters == BOTH_SHELTERS;
    }

    public static NetherChapterState get(MinecraftServer server) {
        return server.getLevel(Level.OVERWORLD).getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean readRecord(int record) {
        if ((record & ALL_RECORDS) != record || Integer.bitCount(record) != 1) {
            throw new IllegalArgumentException("Unknown timetable record");
        }
        if ((annexRecords & record) != 0) return false;
        annexRecords |= record;
        setDirty();
        return true;
    }

    public int recordCount() { return Integer.bitCount(annexRecords); }

    public boolean allRecordsRead() { return annexRecords == ALL_RECORDS; }

    public boolean clearAnnex() {
        if (!allRecordsRead() || annexCleared) return false;
        annexCleared = true;
        setDirty();
        return true;
    }

    public boolean annexCleared() { return annexCleared; }

    public boolean restoreShelter(int shelter) {
        if (shelter != EAST_SHELTER && shelter != WEST_SHELTER) {
            throw new IllegalArgumentException("Unknown bridge shelter");
        }
        if ((bridgeShelters & shelter) != 0) return false;
        bridgeShelters |= shelter;
        setDirty();
        return true;
    }

    public int shelterCount() { return Integer.bitCount(bridgeShelters); }

    public boolean shelterRestored(int shelter) { return (bridgeShelters & shelter) != 0; }

    public boolean bothSheltersRestored() { return bridgeShelters == BOTH_SHELTERS; }

    public boolean clearBridge() {
        if (!bothSheltersRestored() || bridgeCleared) return false;
        bridgeCleared = true;
        setDirty();
        return true;
    }

    public boolean bridgeCleared() { return bridgeCleared; }

    public boolean requiredChaptersCleared() { return annexCleared && bridgeCleared; }
}
