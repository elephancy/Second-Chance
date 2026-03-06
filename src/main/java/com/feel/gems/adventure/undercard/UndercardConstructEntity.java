package com.feel.gems.adventure.undercard;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/** A controlled, non-lootable clockwork opponent for the optional solo bout. */
public final class UndercardConstructEntity extends PathfinderMob {
    public UndercardConstructEntity(EntityType<? extends UndercardConstructEntity> type, Level level) {
        super(type, level);
        setCustomName(Component.literal("Clockwork Sparring Construct"));
        setCustomNameVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0);
    }

    @Override
    protected void registerGoals() {
        // The ring runtime owns the three lane changes and all tells.
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel && tickCount > 40
                && !UndercardRuntime.hasActiveConstruct(getUUID())) {
            // Also clears an orphan loaded from an interrupted server save.
            discard();
        }
    }

    @Override
    protected void dropEquipment(ServerLevel level) {
        // Sparring is never a loot or gem-energy source.
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source,
                                       boolean causedByPlayer) {
        // No drops.
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ARMOR_STAND_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ARMOR_STAND_BREAK;
    }
}
