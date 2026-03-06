package com.feel.gems.adventure.worldgen.site;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Generation-time contact points derived from the two authored v2 templates.
 * One noise column serves all required air heights at that x/z. A corrupt or
 * absent mask rejects new starts; existing saved sites do not use this file.
 */
public final class DimensionContactMask {
    public record Column(int x, int z, int airBits) { }

    private static final String PATH = "/assets/gems/placement/nether_v2_contact.json";
    private static final String ASH = "gems:lastlight_ash_annex_v2";
    private static final String EMBER = "gems:redline_ember_bridge_v2";
    private static final Data DATA = read();

    private record Data(List<Column> ash, List<Column> ember, boolean valid) { }

    private DimensionContactMask() { }

    public static boolean valid() { return DATA.valid; }

    public static List<Column> ash() { return DATA.ash; }

    public static List<Column> ember() { return DATA.ember; }

    private static Data read() {
        try (InputStream stream = DimensionContactMask.class.getResourceAsStream(PATH)) {
            if (stream == null) return invalid();
            JsonObject root = JsonParser.parseReader(new InputStreamReader(
                    stream, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.get("version").getAsInt() != 2
                    || !ASH.equals(root.get("ash_template").getAsString())
                    || !EMBER.equals(root.get("ember_template").getAsString())) {
                return invalid();
            }
            JsonArray emberRaw = root.getAsJsonArray("ember_clearance");
            if (emberRaw == null) return invalid();
            List<Column> ash = columns(root.getAsJsonArray("ash_clearance"));
            List<Column> ember = columns(emberRaw);
            return ash.isEmpty() || (emberRaw.size() != 0 && ember.isEmpty())
                    ? invalid() : new Data(ash, ember, true);
        } catch (IOException | RuntimeException error) {
            return invalid();
        }
    }

    private static List<Column> columns(JsonArray raw) {
        if (raw == null || raw.size() == 0) return List.of();
        List<Column> columns = new ArrayList<>(raw.size());
        Set<Integer> seen = new HashSet<>();
        for (var item : raw) {
            JsonArray values = item.getAsJsonArray();
            if (values.size() != 3) return List.of();
            int x = values.get(0).getAsInt();
            int z = values.get(1).getAsInt();
            int bits = values.get(2).getAsInt();
            if (x < 0 || x > 46 || z < 0 || z > 46 || bits <= 0
                    || (bits & ~0x7fff_ffff) != 0 || !seen.add(x * 47 + z)) {
                return List.of();
            }
            columns.add(new Column(x, z, bits));
        }
        return List.copyOf(columns);
    }

    private static Data invalid() { return new Data(List.of(), List.of(), false); }
}
