package com.feel.gems.adventure.worldgen.site;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Exact authored v6 gallery contact cells, generated with its NBT template. */
public final class DimensionV6GalleryMask {
    public record Column(int x, int z, int airBits, int solidBits) { }

    private static final String PATH =
            "/assets/gems/placement/redline_ember_v6_contact.json";
    private static final String TEMPLATE = "gems:redline_ember_bridge_v6";
    private static final int PUBLIC_AIR = 0x0fe0; // Local Y5..11.
    private static final Data DATA = read();

    private record Data(List<Column> columns, int rockCap, boolean valid) { }

    private DimensionV6GalleryMask() { }

    public static boolean valid() { return DATA.valid; }

    public static List<Column> columns() { return DATA.columns; }

    public static int rockCap() { return DATA.rockCap; }

    private static Data read() {
        try (InputStream stream = DimensionV6GalleryMask.class.getResourceAsStream(PATH)) {
            if (stream == null) return invalid();
            JsonObject root = JsonParser.parseReader(new InputStreamReader(
                    stream, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.get("version").getAsInt() != 6
                    || !TEMPLATE.equals(root.get("template").getAsString())
                    || root.get("rock_replacement_cap").getAsInt() != 768) {
                return invalid();
            }
            JsonArray raw = root.getAsJsonArray("columns");
            // The 15x16 envelope has 48 intentionally untouched side columns.
            if (raw == null || raw.size() != 192) return invalid();
            List<Column> columns = new ArrayList<>(192);
            Set<Integer> seen = new HashSet<>();
            for (var element : raw) {
                JsonArray values = element.getAsJsonArray();
                if (values.size() != 4) return invalid();
                int x = values.get(0).getAsInt();
                int z = values.get(1).getAsInt();
                int air = values.get(2).getAsInt();
                int solid = values.get(3).getAsInt();
                if (x < 32 || x > 46 || z < 55 || z > 70
                        || (air | solid) == 0 || (air & solid) != 0
                        || (air | solid) >>> 31 != 0
                        || !seen.add((z - 55) * 15 + x - 32)
                        || x >= 37 && x <= 41
                            && (air & PUBLIC_AIR) != PUBLIC_AIR) {
                    return invalid();
                }
                columns.add(new Column(x, z, air, solid));
            }
            return new Data(List.copyOf(columns), 768, true);
        } catch (Exception error) {
            return invalid();
        }
    }

    private static Data invalid() { return new Data(List.of(), 0, false); }
}
