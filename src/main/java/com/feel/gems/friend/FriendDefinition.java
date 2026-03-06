package com.feel.gems.friend;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** A public identity and its short, curated interaction lines. */
public record FriendDefinition(String id, String name, List<String> aliases, String hint,
                               List<String> lines, FriendReaction reaction, boolean silent) {
    public FriendDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(aliases, "aliases");
        Objects.requireNonNull(hint, "hint");
        Objects.requireNonNull(lines, "lines");
        Objects.requireNonNull(reaction, "reaction");
        if (!id.matches("[a-z0-9_]{1,32}")) {
            throw new IllegalArgumentException("Invalid friend id: " + id);
        }
        name = name.trim();
        if (name.isEmpty() || name.length() > 32) {
            throw new IllegalArgumentException("Invalid display name for " + id);
        }
        aliases = aliases.stream().map(String::trim).toList();
        for (String alias : aliases) {
            if (alias.isEmpty() || alias.length() > 32 || !alias.equals(alias.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Invalid alias for " + id);
            }
        }
        aliases = List.copyOf(aliases);
        hint = hint.trim();
        if (hint.length() > 110 || hint.indexOf('\n') >= 0 || hint.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Invalid hint for " + id);
        }
        if (lines.size() > 16) {
            throw new IllegalArgumentException("Too many dialogue lines for " + id);
        }
        lines = lines.stream().map(String::trim).toList();
        for (String line : lines) {
            if (line.isEmpty() || line.length() > 100 || line.indexOf('\n') >= 0 || line.indexOf('\r') >= 0) {
                throw new IllegalArgumentException("Invalid dialogue line for " + id);
            }
        }
        lines = List.copyOf(lines);
        if (silent && (!hint.isEmpty() || !lines.isEmpty() || !reaction.equals(FriendReaction.NONE))) {
            throw new IllegalArgumentException("Silent friend cannot have dialogue for " + id);
        }
    }
}
