package com.feel.gems.client.friend;

import com.feel.gems.friend.FriendDialoguePayload;
import com.feel.gems.friend.FriendRegistry;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;

/** A compact card for group quotes and the route epilogue. */
public final class FriendDialogueScreen extends Screen {
    private static final int BACKGROUND = 0xF0182029;
    private static final int PAPER = 0xFFEBE1CE;
    private static final int MUTED = 0xFFB8B9B6;
    private static final int[] ACCENTS = {0xFF77C7BF, 0xFFE8AD7C, 0xFFC6A9DF, 0xFFE4D37E};

    private final FriendDialoguePayload dialogue;
    private final boolean endingScene;
    private Mode mode;
    private Button leadButton;
    private Button updateButton;
    private Button quotesButton;
    private Button previousButton;
    private Button nextButton;
    private int lineIndex;
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private int bodyTop;
    private int bodyBottom;
    private int bodyScroll;
    private int bodyMaxScroll;
    private boolean compact;
    private boolean veryCompact;

    private FriendDialogueScreen(FriendDialoguePayload dialogue) {
        super(Component.literal(dialogue.name()));
        this.dialogue = dialogue;
        endingScene = dialogue.isEndingScene();
        mode = !dialogue.hint().isEmpty() ? Mode.LEAD
                : !dialogue.reaction().isEmpty() ? Mode.UPDATE
                : !dialogue.lines().isEmpty() ? Mode.QUOTES : Mode.LEAD;
    }

    public static void show(Minecraft client, FriendDialoguePayload dialogue) {
        client.gui.setScreen(new FriendDialogueScreen(dialogue));
    }

    @Override
    protected void init() {
        panelWidth = Math.min(width - 12, 440);
        panelHeight = Math.min(height - 12, 250);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        compact = panelHeight < 210 || panelWidth < 330;
        veryCompact = panelHeight < 160;

        int tabY = top + (veryCompact ? 31 : compact ? 41 : 56);
        int tabHeight = compact ? 16 : 20;
        int tabWidth = Math.min(76, (panelWidth - 44) / 3);
        int tabX = left + 16;
        if (!dialogue.hint().isEmpty()) {
            leadButton = addRenderableWidget(Button.builder(Component.literal("Lead"), button -> changeMode(Mode.LEAD))
                    .bounds(tabX, tabY, tabWidth, tabHeight).build());
            tabX += tabWidth + 6;
        }
        if (!dialogue.reaction().isEmpty()) {
            updateButton = addRenderableWidget(Button.builder(Component.literal("Check in"), button -> changeMode(Mode.UPDATE))
                    .bounds(tabX, tabY, tabWidth, tabHeight).build());
            tabX += tabWidth + 6;
        }
        if (!dialogue.lines().isEmpty()) {
            quotesButton = addRenderableWidget(Button.builder(Component.literal(endingScene ? "Story" : "Quotes"),
                            button -> changeMode(Mode.QUOTES))
                    .bounds(tabX, tabY, tabWidth, tabHeight).build());
        }

        int buttonY = top + panelHeight - (compact ? 23 : 30);
        int buttonHeight = compact ? 17 : 20;
        bodyTop = tabY + tabHeight + (compact ? 5 : 15);
        bodyBottom = buttonY - (compact ? 5 : 9);
        boolean narrow = panelWidth < 300;
        int quoteWidth = narrow ? Math.max(34, (panelWidth - 112) / 2) : 78;
        int nextX = left + (narrow ? 20 + quoteWidth : 100);
        if (dialogue.lines().size() > 1) {
            previousButton = addRenderableWidget(Button.builder(Component.literal(narrow ? "Prev" : "Previous"), button -> changeLine(-1))
                    .bounds(left + (narrow ? 10 : 16), buttonY, quoteWidth, buttonHeight).build());
            nextButton = addRenderableWidget(Button.builder(Component.literal("Next"), button -> changeLine(1))
                    .bounds(nextX, buttonY, narrow ? quoteWidth : 64, buttonHeight).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Close"), button -> onClose())
                .bounds(left + panelWidth - (narrow ? 72 : 82), buttonY,
                        narrow ? 62 : 66, buttonHeight).build());
        changeMode(mode);
    }

    private void changeLine(int step) {
        lineIndex = Math.floorMod(lineIndex + step, dialogue.lines().size());
        bodyScroll = 0;
    }

    private void changeMode(Mode next) {
        mode = next;
        bodyScroll = 0;
        if (leadButton != null) leadButton.active = next != Mode.LEAD;
        if (updateButton != null) updateButton.active = next != Mode.UPDATE;
        if (quotesButton != null) quotesButton.active = next != Mode.QUOTES;
        if (previousButton != null) previousButton.visible = next == Mode.QUOTES;
        if (nextButton != null) nextButton.visible = next == Mode.QUOTES;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        extractBackground(context, mouseX, mouseY, delta);
        int accent = ACCENTS[Math.floorMod(dialogue.friendId().hashCode(), ACCENTS.length)];
        context.fillGradient(left, top, left + panelWidth, top + panelHeight,
                BACKGROUND, 0xF00D141D);
        context.fill(left, top, left + panelWidth, top + 3, accent);
        int headerDivider = top + (veryCompact ? 28 : compact ? 38 : 49);
        context.fill(left + 14, headerDivider, left + panelWidth - 14, headerDivider + 1, 0xFF52616A);
        context.fill(left + 14, bodyBottom + (compact ? 2 : 4),
                left + panelWidth - 14, bodyBottom + (compact ? 3 : 5), 0xFF52616A);

        int portraitSize = veryCompact ? 19 : compact ? 25 : 34;
        int portraitX = left + (veryCompact ? 11 : 17);
        int portraitY = top + (veryCompact ? 5 : 10);

        if (FriendRegistry.isTownFriendId(dialogue.friendId())) {
            Identifier skin = Identifier.fromNamespaceAndPath("gems", "textures/entity/friend/" + dialogue.friendId() + ".png");
            context.fill(portraitX - 3, portraitY - 3, portraitX + portraitSize + 3, portraitY + portraitSize + 3, accent);
            context.fill(portraitX - 1, portraitY - 1, portraitX + portraitSize + 1, portraitY + portraitSize + 1, 0xFF101923);
            context.blit(RenderPipelines.GUI_TEXTURED, skin,
                    portraitX, portraitY, 8.0F, 8.0F, portraitSize, portraitSize, 8, 8, 64, 64);
            context.blit(RenderPipelines.GUI_TEXTURED, skin,
                    portraitX, portraitY, 40.0F, 8.0F, portraitSize, portraitSize, 8, 8, 64, 64);
        } else {
            drawFrameMark(context, portraitX, portraitY, accent);
        }
        int headerX = left + (veryCompact ? 42 : compact ? 51 : 64);
        String cardLabel = endingScene
                ? (compact ? "EPILOGUE" : "SECOND CHANCE EPILOGUE")
                : (compact ? "SECOND CHANCE" : "A SECOND CHANCE ENCOUNTER");
        context.text(font, cardLabel,
                headerX, top + (veryCompact ? 4 : compact ? 7 : 12), accent);
        context.text(font, Component.literal(dialogue.name()),
                headerX, top + (veryCompact ? 16 : compact ? 21 : 27), PAPER);

        int textX = left + 18;
        int textWidth = panelWidth - 36;
        int y = bodyTop;
        context.fill(textX, y - 5, left + panelWidth - 18,
                bodyBottom, 0xFF26333C);
        context.fill(textX, y - 5, textX + 3,
                bodyBottom, accent);
        String heading = switch (mode) {
            case LEAD -> "A WAY FORWARD";
            case UPDATE -> "CHECK IN";
            case QUOTES -> endingScene ? "AFTER THE COURT" : "IN THEIR WORDS";
        };
        context.text(font, heading, textX + 11, y + 3, accent, false);
        String message = switch (mode) {
            case LEAD -> dialogue.hint().isEmpty() ? "No lead yet. Explore and ask again." : dialogue.hint();
            case UPDATE -> dialogue.reaction();
            case QUOTES -> dialogue.lines().get(lineIndex);
        };
        drawWrapped(context, message, textX + 11, y + (compact ? 17 : 22), textWidth - 22, PAPER);
        if (mode == Mode.QUOTES) {
            String page = (lineIndex + 1) + " / " + dialogue.lines().size();
            context.text(font, page,
                    left + panelWidth - 18 - font.width(page),
                    y + 3, MUTED, false);
        }
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    private void drawWrapped(GuiGraphicsExtractor context, String value, int x, int y, int maxWidth, int color) {
        List<FormattedCharSequence> wrapped = font.split(Component.literal(value), maxWidth);
        int capacity = Math.max(1, (bodyBottom - y - 3) / 11);
        int visible = wrapped.size() > capacity ? Math.max(1, capacity - 1) : capacity;
        bodyMaxScroll = Math.max(0, wrapped.size() - visible);
        bodyScroll = Math.clamp(bodyScroll, 0, bodyMaxScroll);
        for (int i = bodyScroll; i < Math.min(wrapped.size(), bodyScroll + visible); i++) {
            context.text(font, wrapped.get(i), x, y + (i - bodyScroll) * 11, color, false);
        }
        if (bodyMaxScroll > 0) {
            String position = "Scroll " + (bodyScroll + 1) + "/" + (bodyMaxScroll + 1);
            context.text(font, position, x, bodyBottom - 12, MUTED, false);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (bodyMaxScroll > 0 && mouseX >= left + 18 && mouseX < left + panelWidth - 18
                && mouseY >= bodyTop - 5 && mouseY < bodyBottom && verticalAmount != 0) {
            bodyScroll = Math.clamp(bodyScroll - (int) Math.signum(verticalAmount), 0, bodyMaxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent key) {
        int step = switch (key.key()) {
            case InputConstants.KEY_UP -> -1;
            case InputConstants.KEY_DOWN -> 1;
            case InputConstants.KEY_PAGEUP -> -3;
            case InputConstants.KEY_PAGEDOWN -> 3;
            default -> 0;
        };
        if (bodyMaxScroll > 0 && step != 0) {
            bodyScroll = Math.clamp(bodyScroll + step, 0, bodyMaxScroll);
            return true;
        }
        return super.keyPressed(key);
    }

    private static void drawFrameMark(GuiGraphicsExtractor context, int x, int y, int accent) {
        context.fill(x, y, x + 18, y + 2, accent);
        context.fill(x, y, x + 2, y + 18, accent);
        context.fill(x + 16, y, x + 18, y + 18, accent);
        context.fill(x, y + 16, x + 18, y + 18, accent);
        context.fill(x + 6, y + 6, x + 12, y + 12, 0xFFEBE1CE);
    }

    private enum Mode {
        LEAD, UPDATE, QUOTES
    }
}
