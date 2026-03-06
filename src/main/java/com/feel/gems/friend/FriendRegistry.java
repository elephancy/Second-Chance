package com.feel.gems.friend;

import com.feel.gems.GemsMod;
import com.feel.gems.adventure.LandmarkKey;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;

/** Reloadable friend identities. A malformed datapack leaves the last valid roster active. */
public final class FriendRegistry {
    // Boss identities remain hidden until their campaign reveal. Only these friends can
    // be selected for an ordinary town spawn.
    private static final Set<String> TOWN_IDS = Set.of(
            "adl", "ali", "fam", "hamed", "khaled", "nour", "omar",
            "othman", "ragab", "soryal", "yehia");
    private static volatile Snapshot current = Snapshot.of(List.of(), Map.of());

    private FriendRegistry() {
    }

    public static Optional<FriendDefinition> find(String idOrAlias) {
        if (idOrAlias == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(current.byName().get(idOrAlias.toLowerCase(Locale.ROOT)));
    }

    public static Optional<FriendDefinition> randomTownFriend(RandomSource random) {
        List<FriendDefinition> candidates = current.townRoster();
        return candidates.isEmpty() ? Optional.empty() : Optional.of(candidates.get(random.nextInt(candidates.size())));
    }

    public static boolean isTownFriendId(String id) {
        return TOWN_IDS.contains(id);
    }

    static void reload(ResourceManager manager) {
        Map<Identifier, Resource> files = manager.listResources("friends", id ->
                id.getNamespace().equals(GemsMod.MOD_ID) && id.getPath().endsWith(".json"));
        if (files.isEmpty()) {
            GemsMod.LOGGER.error("No friend identities found under data/gems/friends; keeping previous roster");
            return;
        }
        try {
            List<FriendDefinition> next = new ArrayList<>();
            Map<String, FriendDefinition> index = new HashMap<>();
            for (Identifier id : files.keySet().stream().sorted().toList()) {
                try (Reader reader = files.get(id).openAsReader()) {
                    FriendDefinition definition = parse(id, reader);
                    addKey(index, definition.id(), definition);
                    addKey(index, definition.name().toLowerCase(Locale.ROOT), definition);
                    for (String alias : definition.aliases()) {
                        addKey(index, alias, definition);
                    }
                    next.add(definition);
                }
            }
            current = Snapshot.of(List.copyOf(next), Map.copyOf(index));
            GemsMod.LOGGER.info("Loaded {} friend identities", next.size());
        } catch (Exception e) {
            GemsMod.LOGGER.error("Friend identity reload failed; keeping previous roster", e);
        }
    }

    static FriendDefinition parse(Identifier file, Reader reader) {
        JsonElement root = JsonParser.parseReader(reader);
        if (!root.isJsonObject()) {
            throw new IllegalArgumentException("Friend identity must be an object: " + file);
        }
        JsonObject data = root.getAsJsonObject();
        for (String key : data.keySet()) {
            if (!key.equals("id") && !key.equals("name") && !key.equals("aliases")
                    && !key.equals("hint") && !key.equals("lines") && !key.equals("reaction")
                    && !key.equals("silent")) {
                throw new IllegalArgumentException("Unsupported friend identity field " + key + " in " + file);
            }
        }
        String id = requiredString(data, "id", file);
        String expectedFile = "friends/" + id + ".json";
        if (!file.getPath().equals(expectedFile)) {
            throw new IllegalArgumentException("Friend id does not match filename: " + file);
        }
        String name = requiredString(data, "name", file);
        String hint = data.has("hint") ? requiredString(data, "hint", file) : "";
        boolean silent = false;
        if (data.has("silent")) {
            JsonElement value = data.get("silent");
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
                throw new IllegalArgumentException("Invalid silent flag in " + file);
            }
            silent = value.getAsBoolean();
        }
        List<String> aliases = new ArrayList<>();
        if (data.has("aliases")) {
            JsonArray array = data.getAsJsonArray("aliases");
            for (JsonElement alias : array) {
                if (!alias.isJsonPrimitive() || !alias.getAsJsonPrimitive().isString()) {
                    throw new IllegalArgumentException("Invalid alias in " + file);
                }
                aliases.add(alias.getAsString());
            }
        }
        List<String> lines = new ArrayList<>();
        if (data.has("lines")) {
            JsonArray array = data.getAsJsonArray("lines");
            for (JsonElement line : array) {
                if (!line.isJsonPrimitive() || !line.getAsJsonPrimitive().isString()) {
                    throw new IllegalArgumentException("Invalid dialogue in " + file);
                }
                lines.add(line.getAsString());
            }
        }
        FriendReaction reaction = FriendReaction.NONE;
        if (data.has("reaction")) {
            JsonObject response = data.getAsJsonObject("reaction");
            for (String key : response.keySet()) {
                if (!key.equals("landmark") && !key.equals("before")
                        && !key.equals("visited") && !key.equals("cleared")) {
                    throw new IllegalArgumentException("Unsupported friend reaction field " + key + " in " + file);
                }
            }
            LandmarkKey landmark = null;
            if (response.has("landmark")) {
                String landmarkId = requiredString(response, "landmark", file);
                landmark = Arrays.stream(LandmarkKey.values())
                        .filter(candidate -> candidate.id().equals(landmarkId))
                        .findFirst().orElseThrow(() -> new IllegalArgumentException(
                                "Unknown friend reaction landmark " + landmarkId + " in " + file));
            }
            reaction = new FriendReaction(landmark,
                    requiredString(response, "before", file),
                    requiredString(response, "visited", file),
                    requiredString(response, "cleared", file));
        }
        return new FriendDefinition(id, name, aliases, hint, lines, reaction, silent);
    }

    private static String requiredString(JsonObject data, String key, Identifier file) {
        JsonElement value = data.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Missing or invalid " + key + " in " + file);
        }
        return value.getAsString();
    }

    private static void addKey(Map<String, FriendDefinition> index, String key, FriendDefinition definition) {
        FriendDefinition previous = index.putIfAbsent(key, definition);
        if (previous != null && previous != definition) {
            throw new IllegalArgumentException("Duplicate friend identity key: " + key);
        }
    }

    private record Snapshot(List<FriendDefinition> townRoster, Map<String, FriendDefinition> byName) {
        static Snapshot of(List<FriendDefinition> all, Map<String, FriendDefinition> byName) {
            return new Snapshot(all.stream().filter(definition -> TOWN_IDS.contains(definition.id())).toList(), byName);
        }
    }
}
