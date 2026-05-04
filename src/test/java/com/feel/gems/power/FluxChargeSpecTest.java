package com.feel.gems.power;

import com.feel.gems.power.gem.flux.FluxCharge;
import com.feel.gems.testutil.MinecraftBootstrap;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class FluxChargeSpecTest {
    private static String stringConstant(String fieldName) throws Exception {
        MinecraftBootstrap.ensure();
        Field f = FluxCharge.class.getDeclaredField(fieldName);
        f.setAccessible(true);
        return (String) f.get(null);
    }

    @Test
    void persistentNbtKeysAreStable() throws Exception {
        assertEquals("fluxCharge", stringConstant("KEY_CHARGE"));
        assertEquals("fluxChargeAt100", stringConstant("KEY_AT_100"));
        assertEquals("fluxLastOverchargeTick", stringConstant("KEY_LAST_OVERCHARGE_TICK"));
    }

    @Test
    void clampRespectsSpecBounds() throws Exception {
        MinecraftBootstrap.ensure();
        Method clamp = FluxCharge.class.getDeclaredMethod("clamp", int.class, int.class, int.class);
        clamp.setAccessible(true);

        assertEquals(0, (int) clamp.invoke(null, -5, 0, 200));
        assertEquals(0, (int) clamp.invoke(null, 0, 0, 200));
        assertEquals(1, (int) clamp.invoke(null, 1, 0, 200));
        assertEquals(200, (int) clamp.invoke(null, 200, 0, 200));
        assertEquals(200, (int) clamp.invoke(null, 999, 0, 200));
    }

    @Test
    void persistentKeyFieldsStayPrivateStaticFinal() throws Exception {
        Field charge = FluxCharge.class.getDeclaredField("KEY_CHARGE");
        Field at100 = FluxCharge.class.getDeclaredField("KEY_AT_100");
        Field last = FluxCharge.class.getDeclaredField("KEY_LAST_OVERCHARGE_TICK");

        int required = Modifier.PRIVATE | Modifier.STATIC | Modifier.FINAL;
        assertTrue((charge.getModifiers() & required) == required);
        assertTrue((at100.getModifiers() & required) == required);
        assertTrue((last.getModifiers() & required) == required);
    }
}
