package com.feel.gems.adventure;

import com.feel.gems.state.GemsPersistentDataHolder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/** Issuance receipts save in playerdata alongside the inventory they describe. */
public final class CampaignDeliveryAccount {
    private static final String KEY = "CampaignDelivery";
    private static final String LORE = "lore";
    private static final String SIGNATURE = "signature";
    private static final String SUPPLIES = "supplies";

    private final CompoundTag data;

    private CampaignDeliveryAccount(CompoundTag data) {
        this.data = data;
    }

    public static CampaignDeliveryAccount of(ServerPlayer player,
                                             CampaignPersistentState state) {
        CampaignDeliveryAccount account = in(
                ((GemsPersistentDataHolder) player).gems$getPersistentData());
        state.reconcileDelivered(player.getUUID(), account.loreMask(), account.signatureMask());
        return account;
    }

    static CampaignDeliveryAccount in(CompoundTag root) {
        CompoundTag data = root.getCompound(KEY).orElseGet(() -> {
            CompoundTag created = new CompoundTag();
            root.put(KEY, created);
            return created;
        });
        return new CampaignDeliveryAccount(data);
    }

    int loreMask() {
        return data.getIntOr(LORE, 0) & LandmarkKey.ALL_MASK;
    }

    int signatureMask() {
        return data.getIntOr(SIGNATURE, 0) & LandmarkKey.ALL_MASK;
    }

    int suppliesMask() {
        return data.getIntOr(SUPPLIES, 0) & LandmarkKey.ALL_MASK;
    }

    public boolean hasLore(LandmarkKey landmark) {
        return (loreMask() & landmark.bit()) != 0;
    }

    public boolean hasSignature(LandmarkKey landmark) {
        return (signatureMask() & landmark.bit()) != 0;
    }

    public boolean hasSupplies(LandmarkKey landmark) {
        return (suppliesMask() & landmark.bit()) != 0;
    }

    public void markLore(LandmarkKey landmark) {
        data.putInt(LORE, loreMask() | landmark.bit());
    }

    public void markSignature(LandmarkKey landmark) {
        data.putInt(SIGNATURE, signatureMask() | landmark.bit());
    }

    public void markSupplies(LandmarkKey landmark) {
        data.putInt(SUPPLIES, suppliesMask() | landmark.bit());
    }
}
