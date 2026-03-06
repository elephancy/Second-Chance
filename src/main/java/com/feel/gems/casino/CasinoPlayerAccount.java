package com.feel.gems.casino;

import com.feel.gems.state.GemsPersistentDataHolder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/** Casino money and bonus limits live beside the player's inventory in playerdata. */
final class CasinoPlayerAccount {
    private static final String KEY = "CasinoAccount";
    private static final String PENDING = "pendingStake";
    private static final String CLAIM = "claimEmeralds";
    private static final String TRIVIA_DAY = "triviaBonusDay";
    private static final String COOP_DAY = "coopBonusDay";
    private static final String PROVISIONS_DAY = "provisionsClaimDay";

    private final CompoundTag data;

    private CasinoPlayerAccount(CompoundTag data) {
        this.data = data;
    }

    static CasinoPlayerAccount of(ServerPlayer player) {
        CompoundTag root = ((GemsPersistentDataHolder) player).gems$getPersistentData();
        CompoundTag account = root.getCompound(KEY).orElseGet(() -> {
            CompoundTag created = new CompoundTag();
            root.put(KEY, created);
            return created;
        });
        return new CasinoPlayerAccount(account);
    }

    static CasinoPlayerAccount in(CompoundTag root) {
        CompoundTag account = root.getCompound(KEY).orElseGet(() -> {
            CompoundTag created = new CompoundTag();
            root.put(KEY, created);
            return created;
        });
        return new CasinoPlayerAccount(account);
    }

    int pending() {
        return Math.max(0, data.getIntOr(PENDING, 0));
    }

    long claimBalance() {
        return Math.max(0L, data.getLongOr(CLAIM, 0L));
    }

    void hold(int amount) {
        if (amount <= 0 || amount > 64 || pending() != 0) {
            throw new IllegalStateException("Casino stake already held or invalid");
        }
        data.putInt(PENDING, amount);
    }

    void settle(int payout) {
        if (pending() == 0 || payout < 0) throw new IllegalStateException("No casino stake to settle");
        long next = Math.addExact(claimBalance(), payout);
        data.putInt(PENDING, 0);
        data.putLong(CLAIM, next);
    }

    void forfeit() {
        data.putInt(PENDING, 0);
    }

    void refund() {
        int amount = pending();
        if (amount == 0) return;
        long next = Math.addExact(claimBalance(), amount);
        data.putInt(PENDING, 0);
        data.putLong(CLAIM, next);
    }

    void credit(int amount) {
        if (amount < 0) throw new IllegalArgumentException("Negative casino credit");
        data.putLong(CLAIM, Math.addExact(claimBalance(), amount));
    }

    boolean claimTriviaBonus(long worldDay) {
        return claimBonus(TRIVIA_DAY, worldDay);
    }

    boolean claimCoopBonus(long worldDay) {
        return claimBonus(COOP_DAY, worldDay);
    }

    boolean provisionsClaimed(long worldDay) {
        return data.contains(PROVISIONS_DAY)
                && data.getLongOr(PROVISIONS_DAY, Long.MIN_VALUE) >= worldDay;
    }

    void markProvisionsClaimed(long worldDay) {
        if (!provisionsClaimed(worldDay)) data.putLong(PROVISIONS_DAY, worldDay);
    }

    private boolean claimBonus(String key, long worldDay) {
        if (data.contains(key) && data.getLongOr(key, Long.MIN_VALUE) >= worldDay) return false;
        data.putLong(key, worldDay);
        return true;
    }

    void setClaimBalance(long remaining) {
        if (remaining < 0 || remaining > claimBalance()) throw new IllegalArgumentException("Invalid claim balance");
        data.putLong(CLAIM, remaining);
    }
}
