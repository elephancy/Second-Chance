package com.feel.gems.adventure;

import com.feel.gems.GemsMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** World-wide discoveries for the remaining friend domains. */
public final class FriendDomainUnlockState extends SavedData {
    private static final Codec<FriendDomainUnlockState> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.STRING.listOf().optionalFieldOf("cleared", List.of())
                            .forGetter(state -> state.cleared.stream().sorted().toList())
            ).apply(instance, FriendDomainUnlockState::new));
    private static final SavedDataType<FriendDomainUnlockState> TYPE =
            new SavedDataType<>(Identifier.withDefaultNamespace(GemsMod.MOD_ID + "_friend_domains"),
                    FriendDomainUnlockState::new, CODEC, DataFixTypes.SAVED_DATA_MAP_DATA);

    private final Set<String> cleared = new HashSet<>();

    public FriendDomainUnlockState() {
    }

    private FriendDomainUnlockState(List<String> cleared) {
        this.cleared.addAll(cleared);
    }

    public static FriendDomainUnlockState get(MinecraftServer server) {
        return server.getLevel(Level.OVERWORLD).getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean cleared(String domain) {
        return cleared.contains(domain);
    }

    public boolean clear(String domain) {
        if (!cleared.add(domain)) return false;
        setDirty();
        return true;
    }
}
