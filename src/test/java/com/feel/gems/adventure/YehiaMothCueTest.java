package com.feel.gems.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class YehiaMothCueTest {
    @Test
    void foldedWingsFollowSharedRouteWithoutAdvancingIt() {
        YehiaStoryState story = new YehiaStoryState();
        assertEquals(YehiaMothCue.FOLDED, YehiaMothCue.phase(story));
        assertEquals("message.gems.storybook_foldwing.stories", YehiaMothCue.messageKey(story));
        story.record(YehiaStoryState.TRUTH);
        story.record(YehiaStoryState.PROP);
        assertEquals("message.gems.storybook_foldwing.table", YehiaMothCue.messageKey(story));
        assertFalse(story.started());
        story.start();
        assertEquals(YehiaMothCue.MUSHROOM, YehiaMothCue.phase(story));
        story.mark(YehiaStoryState.MUSHROOM);
        assertEquals(YehiaMothCue.LANTERN, YehiaMothCue.phase(story));
        story.mark(YehiaStoryState.TREE); // A wrong step restarts without penalty.
        assertEquals(YehiaMothCue.MUSHROOM, YehiaMothCue.phase(story));
        story.mark(YehiaStoryState.MUSHROOM);
        story.mark(YehiaStoryState.LANTERN);
        assertEquals(YehiaMothCue.TREE, YehiaMothCue.phase(story));
        assertEquals("message.gems.storybook_foldwing.tree", YehiaMothCue.messageKey(story));
        story.mark(YehiaStoryState.TREE);
        assertEquals(YehiaMothCue.FINISHED, YehiaMothCue.phase(story));
        assertEquals("message.gems.storybook_foldwing.finished", YehiaMothCue.messageKey(story));
    }
}
