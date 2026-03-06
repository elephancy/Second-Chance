package com.feel.gems.adventure;

import com.feel.gems.adventure.CampaignRouteState.Path;

/** Short, authored responses to the world's route, separate from archived chat quotes. */
public final class RouteFriendReactions {
    private RouteFriendReactions() { }

    public static String line(Path path, String friendId, boolean ended) {
        if (path == null || friendId == null) return "";
        return switch (path) {
            case REMEMBER -> switch (friendId) {
                case "hamed" -> ended
                        ? "The new archive keeps both prints. Nobody has to erase a page to make their own count."
                        : "Bring both prints to the Court. The parts that disagree belong in the record too.";
                case "othman" -> ended
                        ? "The catalog now shows what we chose to share, and what we still do not know."
                        : "My public lead is only one account. Take the other witness with you as well.";
                case "yehia" -> ended
                        ? "People can tell the garden story either way now. I like hearing where they differ."
                        : "The ending will change if you leave a voice out. Read every mark before you write it.";
                case "soryal" -> ended
                        ? "The archive kept the accounts. I still have to make the next crossing hold at the yard."
                        : "Keep the accounts, but do not forget the fittings that still need work.";
                default -> "";
            };
            case REPAIR -> switch (friendId) {
                case "soryal" -> ended
                        ? "The depot has spare fittings ready. Bring a broken route here and we can make it hold."
                        : "The Court needs a route that stays open. Carry real materials and watch the weak joints.";
                case "khaled" -> ended
                        ? "Those shelter marks are still there. Take the safe crossing when the water rises."
                        : "Keep someone under cover while the others patch the line. A fast crossing is not enough.";
                case "fam" -> ended
                        ? "The relief board is public now. Everyone can see which route is safe to use."
                        : "Tell the group where the safe route is. A repair is useful only if people can find it.";
                case "othman" -> ended
                        ? "The depot made a safe route. The accounts that disagree still need a public place."
                        : "Fix the road first if people need it. Save the conflicting records too; they will not mend themselves.";
                default -> "";
            };
            case RELEASE -> switch (friendId) {
                case "nour" -> ended
                        ? "The new signal points are live. Anyone can answer the harbor from the crossroads."
                        : "A signal is only a start. Keep both ends talking while the last passage changes.";
                case "ali" -> ended
                        ? "The passage is open when you need it. You can still take the long way if you want."
                        : "The map will shift again at the Court. Mark a way through before it closes.";
                case "fam" -> ended
                        ? "The forum has room for the next message. The route belongs to whoever uses it."
                        : "Carry the message together. Nobody has to make the whole trip alone.";
                case "hamed" -> ended
                        ? "The passage is open. I am still printing the notes that explain where its messages began."
                        : "Send the message, but keep a copy. A fast route can outrun the reason we took it.";
                default -> "";
            };
        };
    }
}
