package com.feel.gems.friend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.feel.gems.adventure.LandmarkKey;
import java.io.StringReader;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

class FriendRegistryTest {
    private static final Identifier FILE = Identifier.fromNamespaceAndPath("gems", "friends/feel.json");

    @Test
    void readsIdentityAndCuratedDialogue() {
        FriendDefinition friend = FriendRegistry.parse(FILE,
                new StringReader("{\"id\":\"feel\",\"name\":\"Feel\",\"aliases\":[\"emily\"],\"hint\":\"Look around.\",\"lines\":[\"Short verified quote\"]}"));
        assertEquals("feel", friend.id());
        assertEquals("Feel", friend.name());
        assertEquals(1, friend.aliases().size());
        assertEquals("Look around.", friend.hint());
        assertEquals("Short verified quote", friend.lines().getFirst());
    }

    @Test
    void refusesUnsupportedFieldsAndMismatchedFilenames() {
        assertThrows(IllegalArgumentException.class, () -> FriendRegistry.parse(FILE,
                new StringReader("{\"id\":\"feel\",\"name\":\"Feel\",\"dialogues\":[\"invented\"]}")));
        assertThrows(IllegalArgumentException.class, () -> FriendRegistry.parse(FILE,
                new StringReader("{\"id\":\"other\",\"name\":\"Other\"}")));
    }

    @Test
    void choosesPersonalVisitAndVictoryReactions() {
        FriendDefinition friend = FriendRegistry.parse(FILE, new StringReader("""
                {"id":"feel","name":"Feel","reaction":{
                  "landmark":"hushwire","before":"Before",
                  "visited":"Visited","cleared":"Cleared"}}
                """));
        FriendReaction reaction = friend.reaction();
        assertEquals("Before", reaction.select(0, 0));
        assertEquals("Before", reaction.select(LandmarkKey.FRAMEFALL.bit(), 0));
        assertEquals("Visited", reaction.select(LandmarkKey.HUSHWIRE.bit(), 0));
        assertEquals("Cleared", reaction.select(0, LandmarkKey.HUSHWIRE.bit()));
        assertThrows(IllegalArgumentException.class, () -> FriendRegistry.parse(FILE,
                new StringReader("""
                        {"id":"feel","name":"Feel","reaction":{
                          "landmark":"unknown","before":"Before",
                          "visited":"Visited","cleared":"Cleared"}}
                        """)));
    }

    @Test
    void silentFriendCannotReceiveDialogueFromADataPack() {
        FriendDefinition friend = FriendRegistry.parse(FILE,
                new StringReader("{\"id\":\"feel\",\"name\":\"Feel\",\"silent\":true}"));
        assertTrue(friend.silent());
        assertTrue(friend.lines().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> FriendRegistry.parse(FILE,
                new StringReader("{\"id\":\"feel\",\"name\":\"Feel\",\"silent\":true,\"hint\":\"Talk to me\"}")));
        assertThrows(IllegalArgumentException.class, () -> FriendRegistry.parse(FILE,
                new StringReader("{\"id\":\"feel\",\"name\":\"Feel\",\"silent\":\"true\"}")));
    }
}
