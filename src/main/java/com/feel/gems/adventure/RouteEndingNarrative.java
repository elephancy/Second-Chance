package com.feel.gems.adventure;

import com.feel.gems.adventure.CampaignRouteState.Path;
import com.feel.gems.friend.FriendDialoguePayload;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/** Authored epilogues for the three locked world routes. These are not chat archive quotes. */
public final class RouteEndingNarrative {
    private RouteEndingNarrative() { }

    public static void open(ServerPlayer player, Path path, int publishedLead) {
        ServerPlayNetworking.send(player, new FriendDialoguePayload(
                FriendDialoguePayload.ENDING_SCENE_PREFIX + path.id(),
                path.label() + " at the Court", "", "",
                pages(path, publishedLead)));
    }

    static List<String> pages(Path path, int publishedLead) {
        String publishedAccount = switch (publishedLead) {
            case OthmanArchiveState.CATALOG -> "Othman: I put the catalog first. The private study stays beside it, marked as another account rather than a mistake.";
            case OthmanArchiveState.STUDY -> "Othman: I put the private study first. The catalog stays beside it, marked as another account rather than a mistake.";
            default -> "Othman: Both accounts are here, but I cannot find which one we chose to publish.";
        };
        return switch (path) {
            case REMEMBER -> List.of(
                    "The Court keeps both witnesses instead of choosing one. At The Square, the Story Council becomes an open archive for the whole world.",
                    publishedAccount,
                    "Hamed: Both prints belong here. They disagree, and neither one needs to disappear.",
                    "Yehia: Let people tell the garden story both ways. The differences are part of what happened here.",
                    "Soryal: A record will not carry spare fittings to the next crossing. I will keep making those at the yard.",
                    "The archive lends an Atlas each day. This world has no relief depot or crossroads desk; repair supplies and new travel kits must come from elsewhere.",
                    "The altered places remain. Their origins are now public, and the record leaves room for people who remember them differently.");
            case REPAIR -> List.of(
                    "The Court's guarded work line holds. The Story Council becomes a relief depot, and the group can use the repaired route again.",
                    "Soryal: The fittings stayed where we put them. Bring the next broken tool to the yard, and we can make it last.",
                    "Khaled: The shelter marks still lead people through the flood. A safe crossing matters when someone is coming back late.",
                    "Othman: I am glad the route is safe. The accounts that disagree still need a place where everyone can read them.",
                    "The depot gives brick and food each day. This world has no open archive or crossroads desk; disputed records remain scattered and travel uses known routes.",
                    "The yard fittings, refuge braces and aid handoff remain. The work is visible, though it did not settle every version of the story.");
            case RELEASE -> List.of(
                    "The Court accepts the last signal. The Story Council becomes a crossroads desk, and the shared message can keep moving through the town.",
                    "Nour: The harbor signal has another answer now. People can follow it from the crossroads instead of waiting at one shore.",
                    "Ali: The cut passage is open. Take the short way when it helps; the long way is still there.",
                    "Hamed: A message can travel faster than its source. I still want the notes printed, so nobody has to guess what was said.",
                    "The crossroads lends chalk and a buoy each day. This world has no open archive or relief depot; it does not preserve every account or stock the next repair.",
                    "The harbor call, Ali's chalk route and Fam's dispatch remain. The group can choose where to go next, while records and repairs stay in their own places.");
        };
    }
}
