package com.feel.gems.adventure;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.server.network.Filterable;
import net.minecraft.network.chat.Component;

/** Post-victory books. No pre-victory item or UI should call this factory. */
public final class IdentityLore {
    private static final Map<LandmarkKey, Entry> ENTRIES = entries();

    private IdentityLore() {
    }

    public static ItemStack create(LandmarkKey landmark) {
        Entry entry = ENTRIES.get(landmark);
        if (entry == null) {
            throw new IllegalArgumentException("No identity lore for " + landmark);
        }
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT,
                new WrittenBookContent(Filterable.passThrough(entry.title()),
                        "The Rumor Atlas", 0,
                        entry.pages().stream()
                                .map(page -> Filterable.<Component>passThrough(Component.literal(page))).toList(),
                        true));
        return book;
    }

    private static Map<LandmarkKey, Entry> entries() {
        EnumMap<LandmarkKey, Entry> result = new EnumMap<>(LandmarkKey.class);
        result.put(LandmarkKey.FRAMEFALL, new Entry("After the Verdict", List.of(
                "The Painted Verdict was Mohanad, whom the others called Teckno. "
                        + "He made the gallery to hold an opinion still long enough to examine it.",
                "He painted, reviewed, revised, and then reviewed the revision. "
                        + "A room that survived his attention became worth keeping. "
                        + "The walls moved because he could always see a better angle.",
                "On the workshop bench: 'i don't like drawing horses'. "
                        + "He left the sketch there anyway.")));
        result.put(LandmarkKey.LASTLIGHT, new Entry("After the Arrival", List.of(
                "The Last Arrival was Zalabany. He joined the others after the place already had stories. "
                        + "That never stopped him from adding a wing to every one of them.",
                "A late annex, a clock, an old vault: all kept different time. "
                        + "He called the repair zalawork. The name stuck before the mortar did.")));
        result.put(LandmarkKey.REHEARSAL, new Entry("After the Punchline", List.of(
                "The Unbroken Punchline was Mostafa, called Tofy. "
                        + "The others aimed jokes at him because he would catch them and throw back something better.",
                "He knew stages, games, and the value of trying a scene again. "
                        + "The props were never meant to be weapons. Once the Works changed, he learned to use them.",
                "A prop tag kept his words: 'hey bro, i never said this project was good, but i did it and i did it well'.")));
        result.put(LandmarkKey.BRANCH, new Entry("After the Second Move", List.of(
                "The Second-Move Solver was Safwat. "
                        + "He could solve a locked room faster than its designer could explain the rules.",
                "He joined when the others joined. He planned every branch except the meeting time. "
                        + "The vault kept his solutions and lost his calendar.",
                "On the back of a solved map: '??? bro thats not how the joke goes'. "
                        + "The vault could never explain the punchline.")));
        result.put(LandmarkKey.REDLINE, new Entry("After the Red Line", List.of(
                "The Red Line Between was Youssef Sameh, called Rubius. "
                        + "When a route collapsed, he had already drawn another one for everyone in it.",
                "He understood people even when they made that difficult. "
                        + "The rescue bridges were a plan, not a trap. "
                        + "The House mistook protecting people for keeping them inside.",
                "Along one bridge plan: 'you turn the point a person is at into the next cliff you gotta climb'.")));
        result.put(LandmarkKey.HUSHWIRE, new Entry("After the Encore", List.of(
                "The Empty Encore was Emily, known here as Feel; other names included Lucy and 0ping. "
                        + "He could make a room argue with one sentence and then ask if everyone got home.",
                "He remembered birthdays, sent people toward music and stories, and built things for the group. "
                        + "The transmitter kept the provocation and lost the concern behind it.",
                "He once lost a rap battle to Strad. Strad has refused the rematch since. "
                        + "The record hall filed this as an unresolved emergency.",
                "A quieter recording survived: 'im down to play again ngl i miss it'.")));
        result.put(LandmarkKey.UNWRITTEN, new Entry("After the Cadenza", List.of(
                "The Stray Cadenza was Youssef Effat, called Strad. "
                        + "He could turn a missed cue into the part everyone remembered.",
                "He fit wherever the others were, kept the work moving, and rarely acted like the stage belonged to him. "
                        + "That is why the Court did: it wrote him into every part.",
                "The steward's room held no crown, only a second script with room for everyone else. "
                        + "He left the last page blank.",
                "In the spare script: 'I could lose a 1v0 right now my cps is down to like 5'. "
                        + "Below it: 'Nah I'd win'. Both lines were his.")));
        return Map.copyOf(result);
    }

    private record Entry(String title, List<String> pages) {
    }
}
