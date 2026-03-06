package com.feel.gems.adventure;

import com.feel.gems.GemsMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.UUIDUtil;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** World-shared conclusion; attempts may be retried without changing the chosen route. */
public final class RouteFinaleState extends SavedData {
    private static final Codec<RouteFinaleState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("complete", false).forGetter(state -> state.complete),
            Codec.INT.optionalFieldOf("repairSupplies", 0).forGetter(state -> state.repairSupplies),
            Codec.unboundedMap(UUIDUtil.AUTHLIB_CODEC, Codec.LONG)
                    .optionalFieldOf("serviceClaims", Map.of()).forGetter(state -> state.serviceClaims)
    ).apply(instance, RouteFinaleState::new));
    private static final SavedDataType<RouteFinaleState> TYPE = new SavedDataType<>(
            Identifier.withDefaultNamespace(GemsMod.MOD_ID + "_route_finale"),
            RouteFinaleState::new, CODEC, DataFixTypes.SAVED_DATA_MAP_DATA);

    private boolean complete;
    private int repairSupplies;
    private final Map<UUID, Long> serviceClaims = new HashMap<>();

    public RouteFinaleState() { }

    private RouteFinaleState(boolean complete, int repairSupplies, Map<UUID, Long> serviceClaims) {
        this.complete = complete;
        this.repairSupplies = repairSupplies & 3;
        this.serviceClaims.putAll(serviceClaims);
    }

    public static RouteFinaleState get(MinecraftServer server) {
        return server.getLevel(Level.OVERWORLD).getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean complete() {
        return complete;
    }

    public boolean finish() {
        if (complete) return false;
        complete = true;
        setDirty();
        return true;
    }

    public int repairSupplies() {
        return repairSupplies;
    }

    public boolean deliverRepairSupply(int bit) {
        if (complete || (bit != 1 && bit != 2) || (repairSupplies & bit) != 0) return false;
        repairSupplies |= bit;
        setDirty();
        return true;
    }

    /** One renewable civic service per player per Minecraft day. */
    public boolean claimService(UUID player, long gameDay) {
        if (!complete || gameDay < 0 || serviceClaims.getOrDefault(player, -1L) >= gameDay) return false;
        serviceClaims.put(player, gameDay);
        setDirty();
        return true;
    }

    public long nextServiceDay(UUID player) {
        return serviceClaims.getOrDefault(player, -1L) + 1;
    }
}
