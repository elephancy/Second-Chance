package com.feel.gems.adventure.exploration;

import com.feel.gems.state.GemsPersistentDataHolder;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerPlayer;

/** Inventory issuance receipts are stored with the player's inventory save. */
public final class ExplorationDeliveryAccount {
    private static final String KEY = "ExplorationDelivery";
    private static final String SUPPLIES = "supplies";
    private static final String FIELD_READY_AT = "fieldReadyAt";
    private final CompoundTag data;

    private ExplorationDeliveryAccount(CompoundTag data) {
        this.data = data;
    }

    public static ExplorationDeliveryAccount of(ServerPlayer player) {
        return in(((GemsPersistentDataHolder) player).gems$getPersistentData());
    }

    static ExplorationDeliveryAccount in(CompoundTag root) {
        CompoundTag data = root.getCompound(KEY).orElseGet(() -> {
            CompoundTag created = new CompoundTag();
            root.put(KEY, created);
            return created;
        });
        return new ExplorationDeliveryAccount(data);
    }

    public Set<String> receipts() {
        ListTag list = data.getListOrEmpty(SUPPLIES);
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < list.size(); i++) {
            String id = list.getStringOr(i, "");
            try {
                ExplorationProgress.validateId(id);
                ids.add(id);
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed values from older or edited playerdata.
            }
        }
        return ids;
    }

    public boolean has(String id) {
        return receipts().contains(id);
    }

    public void mark(String id) {
        ExplorationProgress.validateId(id);
        Set<String> ids = receipts();
        if (!ids.add(id)) return;
        ListTag list = new ListTag();
        ids.stream().sorted().forEach(value -> list.add(StringTag.valueOf(value)));
        data.put(SUPPLIES, list);
    }

    public long fieldReadyAt() {
        return Math.max(0L, data.getLongOr(FIELD_READY_AT, 0L));
    }

    public void setFieldReadyAt(long worldTick) {
        data.putLong(FIELD_READY_AT, Math.max(0L, worldTick));
    }
}
