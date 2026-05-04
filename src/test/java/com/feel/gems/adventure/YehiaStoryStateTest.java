package com.feel.gems.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class YehiaStoryStateTest {
    @Test
    void taleCanStartFromObservedSceneWithoutPlaqueClicks() {
        YehiaStoryState story = new YehiaStoryState();
        assertTrue(story.start());
        story.record(YehiaStoryState.TRUTH);
        assertTrue(story.start());
        story.record(YehiaStoryState.PROP);
        assertTrue(story.start());
        assertFalse(story.mark(YehiaStoryState.MUSHROOM));
        assertEquals(1, story.step());
        assertTrue(story.start());
        assertEquals(1, story.step());
        assertFalse(story.mark(YehiaStoryState.TREE));
        assertEquals(0, story.step());
        assertFalse(story.mark(YehiaStoryState.MUSHROOM));
        assertFalse(story.mark(YehiaStoryState.LANTERN));
        assertTrue(story.mark(YehiaStoryState.TREE));
        assertTrue(story.completed());
        assertFalse(story.start());
    }
}
