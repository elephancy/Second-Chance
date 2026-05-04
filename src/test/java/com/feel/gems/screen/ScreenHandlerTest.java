package com.feel.gems.screen;

import com.feel.gems.core.GemId;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for screen handlers.
 */
public class ScreenHandlerTest {

    @Test
    void gemSeerScreenHandlerHasRequiredMethods() throws Exception {
        Path src = Path.of("gems", "src", "main", "java", "com", "feel", "gems", "screen", "GemSeerScreenHandler.java");
        assertTrue(Files.exists(src), "GemSeerScreenHandler.java should exist");

        String code = Files.readString(src);
        assertTrue(code.contains("extends AbstractContainerMenu"), "Should extend ScreenHandler");
        assertTrue(code.contains("PlayerInfo"), "Should have PlayerInfo record");
        assertTrue(code.contains("getPlayerInfos"), "Should have getPlayerInfos method");
        assertTrue(code.contains("clickMenuButton"), "Should handle button clicks");
        assertTrue(code.contains("formatGemName"), "Should format gem names");
        assertTrue(code.contains("gemColor"), "Should provide gem colors");
        assertTrue(code.contains("energyColor"), "Should provide energy colors");
    }

    @Test
    void pocketsScreenHandlerExists() throws Exception {
        Path src = Path.of("gems", "src", "main", "java", "com", "feel", "gems", "screen", "PocketsScreenHandler.java");
        assertTrue(Files.exists(src), "PocketsScreenHandler.java should exist");

        String code = Files.readString(src);
        // PocketsScreenHandler extends GenericContainerScreenHandler
        assertTrue(code.contains("extends ChestMenu"), "Should extend GenericContainerScreenHandler");
        assertTrue(code.contains("PocketsStorage"), "Should reference PocketsStorage");
    }

    @Test
    void traderScreenHandlerExists() throws Exception {
        Path src = Path.of("gems", "src", "main", "java", "com", "feel", "gems", "screen", "TraderScreenHandler.java");
        assertTrue(Files.exists(src), "TraderScreenHandler.java should exist");

        String code = Files.readString(src);
        assertTrue(code.contains("extends AbstractContainerMenu"), "Should extend ScreenHandler");
    }

    @Test
    void modScreenHandlersRegistersAll() throws Exception {
        Path src = Path.of("gems", "src", "main", "java", "com", "feel", "gems", "screen", "ModScreenHandlers.java");
        assertTrue(Files.exists(src), "ModScreenHandlers.java should exist");

        String code = Files.readString(src);
        // PocketsScreenHandler uses GenericContainerScreenHandler, not registered here
        assertTrue(code.contains("TRADER"), "Should register TRADER handler");
        assertTrue(code.contains("GEM_SEER"), "Should register GEM_SEER handler");
        assertTrue(code.contains("init"), "Should have init method");
    }

    @Test
    void gemSeerFormatGemNameHandlesAllGems() {
        // Test that formatGemName works for all gem IDs
        for (GemId gem : GemId.values()) {
            String formatted = GemSeerScreenHandler.formatGemName(gem);
            assertNotNull(formatted, "formatGemName should not return null for " + gem);
            assertFalse(formatted.isEmpty(), "formatGemName should not return empty for " + gem);
            assertFalse(formatted.contains("_"), "formatGemName should replace underscores for " + gem);
        }
    }

    @Test
    void gemSeerFormatGemNameCapitalizes() {
        assertEquals("Fire", GemSeerScreenHandler.formatGemName(GemId.FIRE));
        assertEquals("Spy", GemSeerScreenHandler.formatGemName(GemId.SPY));
        assertEquals("Void", GemSeerScreenHandler.formatGemName(GemId.VOID));
        assertEquals("Chaos", GemSeerScreenHandler.formatGemName(GemId.CHAOS));
        assertEquals("Prism", GemSeerScreenHandler.formatGemName(GemId.PRISM));
    }

    @Test
    void gemSeerGemColorNeverReturnsNull() {
        for (GemId gem : GemId.values()) {
            var color = GemSeerScreenHandler.gemColor(gem);
            assertNotNull(color, "gemColor should not return null for " + gem);
        }
    }

    @Test
    void gemSeerEnergyColorReturnsExpectedBrackets() {
        // Max energy should be gold
        assertEquals(net.minecraft.ChatFormatting.GOLD, GemSeerScreenHandler.energyColor(10));
        // High energy (7+) should be yellow
        assertEquals(net.minecraft.ChatFormatting.YELLOW, GemSeerScreenHandler.energyColor(8));
        assertEquals(net.minecraft.ChatFormatting.YELLOW, GemSeerScreenHandler.energyColor(7));
        // Medium energy (4-6) should be white
        assertEquals(net.minecraft.ChatFormatting.WHITE, GemSeerScreenHandler.energyColor(6));
        assertEquals(net.minecraft.ChatFormatting.WHITE, GemSeerScreenHandler.energyColor(5));
        assertEquals(net.minecraft.ChatFormatting.WHITE, GemSeerScreenHandler.energyColor(4));
        // Low energy (0-3) should be gray
        assertEquals(net.minecraft.ChatFormatting.GRAY, GemSeerScreenHandler.energyColor(3));
        assertEquals(net.minecraft.ChatFormatting.GRAY, GemSeerScreenHandler.energyColor(2));
        assertEquals(net.minecraft.ChatFormatting.GRAY, GemSeerScreenHandler.energyColor(1));
        assertEquals(net.minecraft.ChatFormatting.GRAY, GemSeerScreenHandler.energyColor(0));
    }

    @Test
    void gemSeerEnergyColorHandlesEdgeCases() {
        // Negative values should return gray
        assertEquals(net.minecraft.ChatFormatting.GRAY, GemSeerScreenHandler.energyColor(-1));
        // Values above 10 should still return gold
        assertEquals(net.minecraft.ChatFormatting.GOLD, GemSeerScreenHandler.energyColor(11));
        assertEquals(net.minecraft.ChatFormatting.GOLD, GemSeerScreenHandler.energyColor(100));
    }
}
