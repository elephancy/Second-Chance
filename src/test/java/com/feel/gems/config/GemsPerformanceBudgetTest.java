package com.feel.gems.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;


public class GemsPerformanceBudgetTest {
    @Test
    void warnsOnHugeRadii() {
        GemsBalanceConfig cfg = new GemsBalanceConfig();
        cfg.fire.heatHazeRadiusBlocks = 128;
        cfg.life.vitalityVortexScanRadiusBlocks = 25;
        cfg.fire.meteorShowerCount = 200;

        var warnings = GemsPerformanceBudget.validate(GemsBalance.Values.from(cfg));
        assertFalse(warnings.isEmpty());
    }
}

