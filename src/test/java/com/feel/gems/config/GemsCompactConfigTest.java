package com.feel.gems.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GemsCompactConfigTest {
    private static final Gson GSON = new Gson();

    @TempDir Path temp;

    @Test
    void writesACompleteReplacementAndRemovesTemporaryFiles() throws IOException {
        Path path = temp.resolve("balance.json");
        GemsConfigManager.write(path, JsonParser.parseString("{\"fire\":{\"cosyCampfireCooldownSeconds\":60}}"));
        assertEquals(60, JsonParser.parseString(Files.readString(path)).getAsJsonObject()
                .getAsJsonObject("fire").get("cosyCampfireCooldownSeconds").getAsInt());
        GemsConfigManager.write(path, new JsonObject());
        assertEquals(new JsonObject(), JsonParser.parseString(Files.readString(path)));
        try (var files = Files.list(temp)) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void aScreenEditKeepsUnrelatedAdvancedAndFutureValues() {
        GemsBalanceConfig before = new GemsBalanceConfig();
        GemsBalanceConfig after = GemsConfigManager.copyForUi(before);
        after.fire.cosyCampfireCooldownSeconds = 60;
        JsonObject disk = JsonParser.parseString("""
                {"fire":{"heatHazeDurationSeconds":99,"cosyCampfireCooldownSeconds":45},
                 "futureFeature":{"newRule":7}}
                """).getAsJsonObject();
        JsonObject defaults = GSON.toJsonTree(new GemsBalanceConfig()).getAsJsonObject();

        GemsConfigManager.applyEditedFields(disk, GSON.toJsonTree(before).getAsJsonObject(),
                GSON.toJsonTree(after).getAsJsonObject(), defaults);
        GemsConfigManager.stripDefaultFields(disk, defaults);

        assertEquals(60, disk.getAsJsonObject("fire").get("cosyCampfireCooldownSeconds").getAsInt());
        assertEquals(99, disk.getAsJsonObject("fire").get("heatHazeDurationSeconds").getAsInt());
        assertEquals(7, disk.getAsJsonObject("futureFeature").get("newRule").getAsInt());
    }

    @Test
    void returningToDefaultRemovesTheOverrideWithoutTouchingOtherFields() {
        GemsBalanceConfig before = new GemsBalanceConfig();
        before.fire.cosyCampfireCooldownSeconds = 60;
        GemsBalanceConfig after = GemsConfigManager.copyForUi(before);
        after.fire.cosyCampfireCooldownSeconds = 45;
        JsonObject disk = JsonParser.parseString("""
                {"fire":{"cosyCampfireCooldownSeconds":60,"heatHazeDurationSeconds":99}}
                """).getAsJsonObject();
        JsonObject defaults = GSON.toJsonTree(new GemsBalanceConfig()).getAsJsonObject();

        GemsConfigManager.applyEditedFields(disk, GSON.toJsonTree(before).getAsJsonObject(),
                GSON.toJsonTree(after).getAsJsonObject(), defaults);
        GemsConfigManager.stripDefaultFields(disk, defaults);

        assertFalse(disk.getAsJsonObject("fire").has("cosyCampfireCooldownSeconds"));
        assertTrue(disk.getAsJsonObject("fire").has("heatHazeDurationSeconds"));
    }

    @Test
    void disablesSaveKeepsConcurrentAndFutureValues() throws IOException {
        Path path = temp.resolve("disables.json");
        Files.writeString(path, """
                {"disabledAbilities":["gems:soul_capture"],"futureRule":{"enabled":true}}
                """);
        GemsDisablesConfig loaded = new GemsDisablesConfig();
        GemsDisablesConfig current = GemsDisablesConfigManager.copyForUi(loaded);
        current.disabledGems = List.of("fire");

        assertTrue(GemsDisablesConfigManager.writeDisables(path, current, loaded));
        JsonObject saved = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        assertEquals("fire", saved.getAsJsonArray("disabledGems").get(0).getAsString());
        assertEquals("gems:soul_capture", saved.getAsJsonArray("disabledAbilities").get(0).getAsString());
        assertTrue(saved.getAsJsonObject("futureRule").get("enabled").getAsBoolean());
    }

    @Test
    void malformedDisablesFileIsNeverOverwrittenByUiSave() throws IOException {
        Path path = temp.resolve("disables.json");
        String malformed = "{broken";
        Files.writeString(path, malformed);
        GemsDisablesConfig loaded = new GemsDisablesConfig();
        GemsDisablesConfig current = GemsDisablesConfigManager.copyForUi(loaded);
        current.disabledGems = List.of("fire");

        assertFalse(GemsDisablesConfigManager.writeDisables(path, current, loaded));
        assertEquals(malformed, Files.readString(path));
    }
}
