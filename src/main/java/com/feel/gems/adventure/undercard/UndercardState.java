package com.feel.gems.adventure.undercard;

import com.feel.gems.GemsMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Shared discovery and personal first-clear ledger; active bouts are transient. */
public final class UndercardState extends SavedData {
    private static final Codec<UndercardState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.listOf().optionalFieldOf("discovered", List.of())
                    .forGetter(state -> state.discovered.stream().sorted().toList()),
            Codec.unboundedMap(UUIDUtil.AUTHLIB_CODEC, Codec.LONG.listOf())
                    .optionalFieldOf("earned", Map.of())
                    .forGetter(state -> state.earned.entrySet().stream().collect(Collectors.toMap(
                            Map.Entry::getKey, row -> row.getValue().stream().sorted().toList())))
    ).apply(instance, UndercardState::new));
    private static final SavedDataType<UndercardState> TYPE = new SavedDataType<>(
            Identifier.withDefaultNamespace(GemsMod.MOD_ID + "_undercard"),
            UndercardState::new, CODEC, DataFixTypes.SAVED_DATA_MAP_DATA);

    private final Set<Long> discovered;
    private final Map<UUID, Set<Long>> earned;

    public UndercardState() {
        discovered = new HashSet<>();
        earned = new HashMap<>();
    }

    private UndercardState(List<Long> discovered, Map<UUID, List<Long>> earned) {
        this.discovered = new HashSet<>(discovered);
        this.earned = new HashMap<>();
        earned.forEach((player, sites) -> this.earned.put(player, new HashSet<>(sites)));
    }

    public static UndercardState get(MinecraftServer server) {
        return server.getLevel(Level.OVERWORLD).getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean isDiscovered(long site) {
        return discovered.contains(site);
    }

    public boolean hasEarned(UUID player, long site) {
        return earned.getOrDefault(player, Set.of()).contains(site);
    }

    public Set<Long> earned(UUID player) {
        return Set.copyOf(earned.getOrDefault(player, Set.of()));
    }

    public boolean complete(long site, Set<UUID> participants) {
        if (participants.isEmpty()) throw new IllegalArgumentException("No undercard participants");
        boolean changed = discovered.add(site);
        for (UUID player : participants) {
            changed |= earned.computeIfAbsent(player, ignored -> new HashSet<>()).add(site);
        }
        if (changed) setDirty();
        return changed;
    }
}
