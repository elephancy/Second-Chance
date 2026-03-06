package com.feel.gems.adventure;

/** A visual reminder of the next shared Storybook Vale step, without advancing it. */
public final class YehiaMothCue {
    public static final int FOLDED = 0;
    public static final int MUSHROOM = 1;
    public static final int LANTERN = 2;
    public static final int TREE = 3;
    public static final int FINISHED = 4;

    private YehiaMothCue() { }

    public static int phase(YehiaStoryState story) {
        if (story.completed()) return FINISHED;
        if (!story.started()) return FOLDED;
        return Math.clamp(story.step() + 1, MUSHROOM, TREE);
    }

    public static String messageKey(YehiaStoryState story) {
        if (story.completed()) return "message.gems.storybook_foldwing.finished";
        if (!story.ready()) return "message.gems.storybook_foldwing.stories";
        if (!story.started()) return "message.gems.storybook_foldwing.table";
        return switch (phase(story)) {
            case MUSHROOM -> "message.gems.storybook_foldwing.mushroom";
            case LANTERN -> "message.gems.storybook_foldwing.lantern";
            default -> "message.gems.storybook_foldwing.tree";
        };
    }

    public static int particleColor(int phase) {
        return switch (phase) {
            case MUSHROOM -> 0xBA7857;
            case LANTERN -> 0xE5B65A;
            case TREE -> 0x70AE77;
            case FINISHED -> 0x83B8C5;
            default -> 0xD9CFAB;
        };
    }
}
