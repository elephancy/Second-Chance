package com.feel.gems.friend;

import com.feel.gems.adventure.LandmarkKey;
import java.util.Objects;

/** Three short, authored responses to one player's campaign progress. */
public record FriendReaction(LandmarkKey landmark, String before, String visited, String cleared) {
    public static final FriendReaction NONE = new FriendReaction(null, "", "", "");

    public FriendReaction {
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(visited, "visited");
        Objects.requireNonNull(cleared, "cleared");
        if (before.isEmpty() != visited.isEmpty() || before.isEmpty() != cleared.isEmpty()) {
            throw new IllegalArgumentException("A friend reaction needs all three stages");
        }
        for (String line : new String[] {before, visited, cleared}) {
            if (line.length() > 140 || line.indexOf('\n') >= 0 || line.indexOf('\r') >= 0) {
                throw new IllegalArgumentException("Friend reaction must be a short single line");
            }
        }
    }

    public String select(int visitedMask, int clearedMask) {
        if (before.isEmpty()) return "";
        int bit = landmark == null ? LandmarkKey.ALL_MASK : landmark.bit();
        if ((clearedMask & bit) != 0) return cleared;
        if ((visitedMask & bit) != 0) return visited;
        return before;
    }
}
