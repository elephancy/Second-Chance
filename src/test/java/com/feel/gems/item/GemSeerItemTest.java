package com.feel.gems.item;

import com.feel.gems.core.GemId;
import com.feel.gems.screen.GemSeerScreenHandler;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class GemSeerItemTest {

    @Test
    void gemSeerItemClassExists() throws Exception {
        Path src = Path.of("gems", "src", "main", "java", "com", "feel", "gems", "item", "legendary", "GemSeerItem.java");
        assertTrue(Files.exists(src), "GemSeerItem.java is missing");

        String code = Files.readString(src);
        assertTrue(code.contains("implements LegendaryItem"), "GemSeerItem should implement LegendaryItem");
        assertTrue(code.contains("openMenu"), "GemSeerItem should open a screen");
        assertTrue(code.contains("GemSeerScreenHandler"), "GemSeerItem should use GemSeerScreenHandler");
        assertFalse(code.contains("COOLDOWN"), "GemSeerItem should not have a cooldown");
    }

    @Test
    void gemSeerScreenHandlerExists() throws Exception {
        Path src = Path.of("gems", "src", "main", "java", "com", "feel", "gems", "screen", "GemSeerScreenHandler.java");
        assertTrue(Files.exists(src), "GemSeerScreenHandler.java is missing");

        String code = Files.readString(src);
        assertTrue(code.contains("extends AbstractContainerMenu"), "GemSeerScreenHandler should extend ScreenHandler");
        assertTrue(code.contains("PlayerInfo"), "GemSeerScreenHandler should have PlayerInfo record");
        assertTrue(code.contains("getPlayerInfos"), "GemSeerScreenHandler should expose player info list");
    }

    @Test
    void gemSeerFormatGemNameCapitalizesWords() {
        assertEquals("Fire", GemSeerScreenHandler.formatGemName(GemId.FIRE));
        assertEquals("Spy", GemSeerScreenHandler.formatGemName(GemId.SPY));
        assertEquals("Void", GemSeerScreenHandler.formatGemName(GemId.VOID));
        assertEquals("Chaos", GemSeerScreenHandler.formatGemName(GemId.CHAOS));
        assertEquals("Prism", GemSeerScreenHandler.formatGemName(GemId.PRISM));
    }

    @Test
    void gemSeerGemColorReturnsFormattingForAllGems() {
        for (GemId gem : GemId.values()) {
            assertNotNull(GemSeerScreenHandler.gemColor(gem), 
                    "gemColor should return non-null for " + gem);
        }
    }

    @Test
    void gemSeerEnergyColorReturnsExpectedValues() {
        // Gold for max energy
        assertEquals(net.minecraft.ChatFormatting.GOLD, GemSeerScreenHandler.energyColor(10));
        // Yellow for high energy
        assertEquals(net.minecraft.ChatFormatting.YELLOW, GemSeerScreenHandler.energyColor(8));
        // White for medium energy
        assertEquals(net.minecraft.ChatFormatting.WHITE, GemSeerScreenHandler.energyColor(5));
        // Gray for low energy
        assertEquals(net.minecraft.ChatFormatting.GRAY, GemSeerScreenHandler.energyColor(2));
    }
}
