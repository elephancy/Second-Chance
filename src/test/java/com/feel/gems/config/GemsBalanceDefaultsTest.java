package com.feel.gems.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;


public class GemsBalanceDefaultsTest {
    @Test
    void defaultsMatchExpectedTicks() {
        GemsBalance.Values v = GemsBalance.Values.defaults();
        assertEquals(3 * 20, v.astra().unboundedDurationTicks());
        assertEquals(10 * 20, v.fire().heatHazeDurationTicks());
        assertEquals(9 * 60 * 20, v.wealth().richRushCooldownTicks());
        assertEquals(3 * 60 * 20, v.wealth().richRushDurationTicks());
        assertEquals(2, v.fire().meteorShowerExplosionPower());
    }

    @Test
    void defaultBudgetHasNoWarnings() {
        GemsBalance.Values v = GemsBalance.Values.defaults();
        assertTrue(GemsPerformanceBudget.validate(v).isEmpty());
    }

    @Test
    void clampsExtremeValues() {
        GemsBalanceConfig cfg = new GemsBalanceConfig();
        cfg.fire.heatHazeRadiusBlocks = 999;
        cfg.life.vitalityVortexScanRadiusBlocks = 999;
        cfg.astra.astralDaggersDamage = -100.0F;
        cfg.fire.fireballMaxDistanceBlocks = 1;
        cfg.fire.meteorShowerExplosionPower = 999;
        cfg.visual.particleScalePercent = 999;
        cfg.visual.maxParticlesPerCall = 99999;
        cfg.flux.fluxBeamRangeBlocks = 0;
        cfg.flux.fluxBeamMaxDamageAt200 = 999.0F;
        cfg.puff.dashHitRangeBlocks = 0.0D;
        cfg.speed.arcShotMaxTargets = 999;
        cfg.strength.chadEveryHits = 0;
        cfg.wealth.richRushCooldownSeconds = 999999999;
        cfg.wealth.richRushDurationSeconds = -5;

        GemsBalance.Values v = GemsBalance.Values.from(cfg);
        assertEquals(32, v.fire().heatHazeRadiusBlocks());
        assertEquals(6, v.life().vitalityVortexScanRadiusBlocks());
        assertEquals(0.0F, v.astra().astralDaggersDamage());
        assertEquals(10, v.fire().fireballMaxDistanceBlocks());
        assertEquals(6, v.fire().meteorShowerExplosionPower());
        assertEquals(200, v.visual().particleScalePercent());
        assertEquals(2048, v.visual().maxParticlesPerCall());
        assertEquals(1, v.flux().fluxBeamRangeBlocks());
        assertEquals(120.0F, v.flux().fluxBeamMaxDamageAt200());
        assertEquals(0.5D, v.puff().dashHitRangeBlocks());
        assertEquals(10, v.speed().arcShotMaxTargets());
        assertEquals(1, v.strength().chadEveryHits());
        assertEquals(24 * 3600 * 20, v.wealth().richRushCooldownTicks());
        assertEquals(0, v.wealth().richRushDurationTicks());
    }

    @Test
    void clampsNaNValuesToSafeMins() {
        GemsBalanceConfig cfg = new GemsBalanceConfig();
        cfg.fire.meteorShowerVelocity = Float.NaN;
        cfg.life.lifeCircleMaxHealthDelta = Double.NaN;
        cfg.astra.astralDaggersVelocity = Float.NaN;

        GemsBalance.Values v = GemsBalance.Values.from(cfg);
        assertEquals(0.1F, v.fire().meteorShowerVelocity());
        assertEquals(0.0D, v.life().lifeCircleMaxHealthDelta());
        assertEquals(0.1F, v.astra().astralDaggersVelocity());
    }

    @Test
    void missingConfigSectionsDoNotCrash() {
        GemsBalanceConfig cfg = new GemsBalanceConfig();
        cfg.visual = null;
        cfg.fire = null;

        GemsBalance.Values v = GemsBalance.Values.from(cfg);
        assertNotNull(v.visual());
        assertNotNull(v.fire());
    }
}
