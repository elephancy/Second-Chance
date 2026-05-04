package com.feel.gems.core;

import com.feel.gems.power.registry.ModAbilities;
import com.feel.gems.power.registry.ModPassives;
import com.feel.gems.testutil.MinecraftBootstrap;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;


public class GemRegistryIntegrityTest {
    @Test
    void allRegisteredPowersExist() {
        MinecraftBootstrap.ensure();
        GemRegistry.init();
        for (GemId gemId : GemId.values()) {
            GemDefinition def = GemRegistry.definition(gemId);

            for (var passive : def.passives()) {
                assertNotNull(ModPassives.get(passive), "Missing passive: " + passive + " for " + gemId);
            }

            for (var ability : def.abilities()) {
                var impl = ModAbilities.get(ability);
                assertNotNull(impl, "Missing ability: " + ability + " for " + gemId);
                assertTrue(impl.cooldownTicks() >= 0, "Negative cooldown for " + ability);
            }
        }
    }
}
