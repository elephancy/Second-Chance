package com.feel.gems.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;


public class HeartRecipeTest {

    private static String ingredientId(JsonObject key, String symbol) {
        var element = key.get(symbol);
        assertNotNull(element, "Missing recipe key entry for '" + symbol + "'");
        if (element.isJsonPrimitive()) {
            return element.getAsString();
        }
        JsonObject obj = element.getAsJsonObject();
        if (obj.has("item")) {
            return obj.get("item").getAsString();
        }
        if (obj.has("tag")) {
            return "#" + obj.get("tag").getAsString();
        }
        fail("Unsupported ingredient format for '" + symbol + "': " + obj);
        return null;
    }

    @Test
    void heartRecipeUsesExpensiveComponents() throws Exception {
        Path recipePath = Path.of("gems", "src", "main", "resources", "data", "gems", "recipe", "heart.json");
        assertTrue(Files.exists(recipePath), "Heart recipe file is missing");

        JsonObject root = JsonParser.parseString(Files.readString(recipePath)).getAsJsonObject();
        assertEquals("minecraft:crafting_shaped", root.get("type").getAsString());

        JsonObject key = root.getAsJsonObject("key");
        assertEquals("minecraft:netherite_scrap", ingredientId(key, "N"));
        assertEquals("minecraft:iron_block", ingredientId(key, "I"));
        assertEquals("minecraft:gold_block", ingredientId(key, "G"));

        var pattern = root.getAsJsonArray("pattern");
        assertEquals(3, pattern.size());
        assertEquals("NIN", pattern.get(0).getAsString());
        assertEquals("IGI", pattern.get(1).getAsString());
        assertEquals("NIN", pattern.get(2).getAsString());

        JsonObject result = root.getAsJsonObject("result");
        assertEquals("gems:heart", result.get("id").getAsString());
        assertEquals(1, result.get("count").getAsInt());
    }
}
