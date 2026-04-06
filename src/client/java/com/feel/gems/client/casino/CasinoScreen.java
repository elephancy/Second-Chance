package com.feel.gems.client.casino;

import com.feel.gems.casino.CasinoActionPayload;
import com.feel.gems.casino.CasinoViewPayload;
import com.feel.gems.client.screen.GemsUiTheme;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.network.chat.Component;

/** Casino table with server-owned choices and a compact, readable play surface. */
public final class CasinoScreen extends Screen {
    private static final int INK = GemsUiTheme.PALE;
    private static final int MUTED = GemsUiTheme.MUTED;
    private static final int GOLD = GemsUiTheme.GOLD;
    private static final int SURFACE = 0xF0132028;
    private static final int FELT = GemsUiTheme.FELT;
    private static final int TILE = 0xFF24424A;
    private static final int BORDER = 0xFF829088;
    private static final Pattern CARD_NAME = Pattern.compile(
            "card_(back|(?:clubs|diamonds|hearts|spades)_(?:0[2-9]|10|a|j|q|k))");
    private static final Pattern WHEEL_RESULT = Pattern.compile(
            "Wheel: (\\d{1,2}) (red|black|green)\\. You chose (red|black|high|low)\\.");
    private static final long RESULT_REVEAL_NANOS = 650_000_000L;
    private static boolean reducedMotion;
    private final long revealStartedAt;
    private CasinoViewPayload view;
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private int buttonTop;
    private boolean rulesOpen;
    private boolean actionPending;
    private final List<Button> decisionButtons = new ArrayList<>();
    private final List<StyledButton> styledButtons = new ArrayList<>();

    private record StyledButton(Button button, String game, String action, boolean secondary) {
    }

    public CasinoScreen(CasinoViewPayload view) {
        this(view, false);
    }

    private CasinoScreen(CasinoViewPayload view, boolean revealResult) {
        super(Component.literal("Second Chance Casino"));
        this.view = view;
        revealStartedAt = revealResult && !reducedMotion ? System.nanoTime() : 0L;
    }

    public static void show(Minecraft client, CasinoViewPayload payload) {
        CasinoScreen current = client.gui.screen() instanceof CasinoScreen screen ? screen : null;
        if (current != null && current.view.sessionId().equals(payload.sessionId())
                && current.view.revision() > payload.revision()) return;
        boolean revealResult = current != null && current.view.sessionId().equals(payload.sessionId())
                && current.view.phase().equals("ROUND") && payload.phase().equals("RESULT")
                && (payload.gameId().equals("wheel") || payload.gameId().equals("reels"));
        client.gui.setScreen(new CasinoScreen(payload, revealResult));
    }

    @Override
    protected void init() {
        panelWidth = Math.min(width - 16, 540);
        panelHeight = Math.min(height - 16, preferredPanelHeight());
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        List<CasinoViewPayload.Choice> choices = view.choices();
        int columns = choices.size() > 1 ? 2 : 1;
        int rows = (choices.size() + columns - 1) / columns;
        int gap = 6;
        int buttonStep = isLobby() ? panelHeight < 260 ? 25 : 31 : panelHeight < 240 ? 22 : 25;
        int buttonHeight = isLobby() ? panelHeight < 260 ? 21 : 26 : panelHeight < 240 ? 18 : 20;
        int buttonWidth = (panelWidth - 32 - gap * (columns - 1)) / columns;
        int lobbyIntroLines = view.phase().equals("TOURNAMENT_LOBBY")
                ? font.split(Component.literal(view.rules()), panelWidth - 36).size() : 1;
        buttonTop = isLobby() ? top + 81 + (lobbyIntroLines - 1) * 11
                : top + panelHeight - 32 - rows * buttonStep;
        decisionButtons.clear();
        styledButtons.clear();
        for (int i = 0; i < choices.size(); i++) {
            CasinoViewPayload.Choice choice = choices.get(i);
            int column = i % columns;
            int row = i / columns;
            int choiceWidth = view.gameId().equals("coop") && choices.size() == 3 && i == 2
                    ? panelWidth - 32 : buttonWidth;
            Button button = addRenderableWidget(Button.builder(Component.literal(choice.label()), ignored -> choose(choice.id()))
                    .bounds(left + 16 + column * (buttonWidth + gap), buttonTop + row * buttonStep,
                            choiceWidth, buttonHeight).build());
            decisionButtons.add(button);
            styledButtons.add(new StyledButton(button, isLobby() ? gameForChoice(choice) : view.gameId(),
                    choice.id(), false));
        }
        if (!isLobby() && !isPreview()) {
            Button rules = addRenderableWidget(Button.builder(Component.literal("Show rules"), button -> {
                rulesOpen = !rulesOpen;
                button.setMessage(Component.literal(rulesOpen ? "Back to table" : "Show rules"));
                for (Button decision : decisionButtons) decision.visible = !rulesOpen;
            })
                    .bounds(left + panelWidth - 199, top + panelHeight - 27, 97, 18).build());
            styledButtons.add(new StyledButton(rules, view.gameId(), "", true));
        }
        if (panelWidth >= 320) {
            Button motion = addRenderableWidget(Button.builder(
                    Component.literal(reducedMotion ? "Motion: Off" : "Motion: On"), button -> {
                        reducedMotion = !reducedMotion;
                        button.setMessage(Component.literal(reducedMotion ? "Motion: Off" : "Motion: On"));
                    }).bounds(left + 16, top + panelHeight - 27, 99, 18).build());
            motion.setTooltip(Tooltip.create(Component.literal(
                    "Turn off motion to show Wheel and Reels results instantly.")));
            styledButtons.add(new StyledButton(motion, view.gameId(), "", true));
        }
        Button leave = addRenderableWidget(Button.builder(Component.literal(isLiveRound() ? "Close view" : "Leave table"), button -> onClose())
                .bounds(left + panelWidth - 96, top + panelHeight - 27, 80, 18).build());
        if (isLiveRound()) {
            leave.setTooltip(Tooltip.create(Component.literal(
                    "Your round stays open when you close this view. Return to the table to continue.")));
        }
        styledButtons.add(new StyledButton(leave, view.gameId(), "", true));
    }

    private int preferredPanelHeight() {
        if (!view.gameId().equals("coop")) return 338;
        return switch (view.phase()) {
            case "PREVIEW" -> 218;
            case "COOP_WAIT" -> 202;
            case "COOP_PLAY" -> view.choices().isEmpty() ? 250 : 324;
            case "RESULT" -> 158 + 11 * Math.max(0,
                    font.split(Component.literal(view.message()), panelWidth - 36).size() - 1);
            default -> 338;
        };
    }

    private static String gameForChoice(CasinoViewPayload.Choice choice) {
        String id = choice.id().toLowerCase(java.util.Locale.ROOT);
        String label = choice.label().toLowerCase(java.util.Locale.ROOT);
        if (id.contains("rps") || label.contains("rock paper")) return "rps";
        if (id.contains("card") || label.contains("twenty-one")) return "cards";
        if (id.contains("wheel") || label.contains("color wheel")) return "wheel";
        if (id.contains("reel") || label.contains("hold and spin")) return "reels";
        if (id.contains("trivia") || label.contains("trivia")) return "trivia";
        if (id.contains("coop") || label.contains("signal table")) return "coop";
        return "lobby";
    }

    private void choose(String action) {
        if (actionPending || rulesOpen) return;
        actionPending = true;
        for (Button button : decisionButtons) button.active = false;
        ClientPlayNetworking.send(new CasinoActionPayload(view.sessionId(), view.revision(), action));
    }

    private boolean isLobby() {
        return view.phase().equals("LOBBY") || view.phase().equals("TOURNAMENT_LOBBY");
    }

    private boolean isLiveRound() {
        return view.phase().equals("ROUND") || view.phase().equals("COOP_PLAY");
    }

    private boolean isPreview() {
        return view.phase().equals("PREVIEW");
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        extractBackground(context, mouseX, mouseY, delta);
        int accent = accent(view.gameId());
        context.fill(left - 3, top - 3, left + panelWidth + 3, top + panelHeight + 3, 0xDD080F14);
        context.fillGradient(left, top, left + panelWidth, top + panelHeight, SURFACE, 0xF00D1820);
        context.fill(left, top, left + panelWidth, top + 3, accent);
        context.fill(left, top, left + 1, top + panelHeight, BORDER);
        context.fill(left + panelWidth - 1, top, left + panelWidth, top + panelHeight, BORDER);
        context.fill(left, top + panelHeight - 1, left + panelWidth, top + panelHeight, BORDER);
        context.fill(left + 10, top + 35, left + panelWidth - 10, top + panelHeight - 34, FELT);
        context.fill(left + 10, top + 35, left + panelWidth - 10, top + 36, 0xFF426267);
        context.fill(left + 10, top + panelHeight - 35, left + panelWidth - 10,
                top + panelHeight - 34, 0xFF426267);
        context.fill(left + 16, top + 29, left + panelWidth - 16, top + 30, BORDER);
        drawFeltInlay(context, accent);

        drawTrimmed(context, view.title(), left + 16, top + 12,
                panelWidth - 125, INK);
        String balance = view.emeralds() + " EMERALDS";
        int balanceX = left + panelWidth - 14 - font.width(balance);
        context.fill(balanceX - 7, top + 8, left + panelWidth - 8, top + 25, 0xFF263F3F);
        context.fill(balanceX - 7, top + 8, balanceX - 5, top + 25, GOLD);
        context.text(font, balance,
                balanceX, top + 12, GOLD);

        if (!isLobby()) {
            drawRoundStatus(context, accent);
        }
        if (isLobby()) {
            drawLobby(context, accent);
        } else if (isPreview() || rulesOpen) {
            drawRules(context, accent);
        } else {
            int contentBottom = buttonTop - 6;
            if (view.choices().isEmpty()) contentBottom = top + panelHeight - 43;
            boolean sceneAboveBoard = panelHeight >= 300
                    && contentBottom - (top + 65) >= 145;
            if (sceneAboveBoard) drawSceneArt(context, left + 16, top + 68, panelWidth - 32, 44);
            int y = drawWrapped(context, view.message(), left + 18,
                    top + (sceneAboveBoard ? 119 : 65),
                    panelWidth - 36, accent, contentBottom);
            List<String> board = view.board();
            if (!board.isEmpty() && board.getFirst().equals(view.message())) {
                board = board.subList(1, board.size());
            }
            if (view.gameId().equals("coop") && view.phase().equals("COOP_PLAY")) {
                board = board.stream().filter(line -> !line.startsWith("Tell your partner your color.")).toList();
            }
            drawBoard(context, board, y + 10, contentBottom, accent, sceneAboveBoard);
        }
        super.extractRenderState(context, mouseX, mouseY, delta);
        drawStyledButtons(context);
    }

    private void drawFeltInlay(GuiGraphicsExtractor context, int accent) {
        int x0 = left + 12;
        int x1 = left + panelWidth - 12;
        int y0 = top + 38;
        int y1 = top + panelHeight - 37;
        context.fill(x0 + 4, y0, x1 - 4, y0 + 1, 0xFF35565B);
        context.fill(x0 + 4, y1 - 1, x1 - 4, y1, 0xFF35565B);
        for (int x = x0 + 10; x < x1 - 9; x += 22) {
            context.fill(x, y0 + 4, x + 1, y0 + 7, 0xFF557177);
            context.fill(x, y1 - 7, x + 1, y1 - 4, 0xFF557177);
        }
        context.fill(x0, y0 + 12, x0 + 2, y0 + 29, accent);
        context.fill(x1 - 2, y0 + 12, x1, y0 + 29, accent);
    }

    private void drawStyledButtons(GuiGraphicsExtractor context) {
        for (StyledButton styled : styledButtons) {
            Button button = styled.button();
            if (!button.visible) continue;
            int x = button.getX();
            int y = button.getY();
            int right = x + button.getWidth();
            int bottom = y + button.getHeight();
            int accent = accent(styled.game());
            boolean highlighted = button.isHoveredOrFocused() && button.active;
            int face = !button.active ? 0xFF203139 : highlighted ? 0xFF2C4F55 : 0xFF182D35;
            int edge = !button.active ? 0xFF46575B : highlighted ? accent : 0xFF547078;
            context.fill(x, y, right, bottom, edge);
            context.fill(x + 1, y + 1, right - 1, bottom - 1, face);
            context.fill(x + 2, y + 2, right - 2, y + 3, highlighted ? accent : 0xFF45656A);
            if (!styled.secondary()) {
                context.fill(x + 2, y + 3, x + 5, bottom - 3, accent);
                drawChoiceMark(context, styled.game(), styled.action(),
                        x + 10, y + (button.getHeight() - 18) / 2);
            }
            String label = button.getMessage().getString();
            int labelX = styled.secondary() ? x + button.getWidth() / 2 - font.width(label) / 2
                    : x + 33;
            int allowed = styled.secondary() ? button.getWidth() - 12 : button.getWidth() - 42;
            drawTrimmed(context, label, labelX, y + (button.getHeight() - 8) / 2,
                    allowed, button.active ? INK : MUTED);
            if (highlighted) {
                context.fill(x + 5, bottom - 2, right - 5, bottom - 1, accent);
            }
        }
    }

    private void drawChoiceMark(GuiGraphicsExtractor context, String game, String action, int x, int y) {
        if (game.equals("rps")) {
            int cropX = switch (action) {
                case "rock" -> 72;
                case "paper" -> 140;
                case "scissors" -> 206;
                default -> -1;
            };
            if (cropX >= 0) {
                Identifier texture = Identifier.fromNamespaceAndPath("gems",
                        "textures/gui/casino/tables/rps_scene.png");
                context.blit(RenderPipelines.GUI_TEXTURED, texture, x, y,
                        (float) cropX, 8.0F, 18, 18, 40, 40, 320, 56);
                return;
            }
        }
        if (game.equals("reels") && action.startsWith("hold_") && !view.board().isEmpty()) {
            String[] symbols = reelSymbols(view.board().getFirst());
            int index = action.charAt(action.length() - 1) - '0';
            if (symbols != null && index >= 0 && index < symbols.length) {
                drawReelSymbol(context, symbols[index], x, y, 18);
                return;
            }
        }
        String token = switch (game) {
            case "wheel" -> switch (action) {
                case "red" -> "R";
                case "black" -> "B";
                case "high" -> "H";
                case "low" -> "L";
                default -> "";
            };
            case "cards" -> switch (action) {
                case "hit" -> "H";
                case "stand" -> "S";
                default -> "";
            };
            case "trivia" -> switch (action) {
                case "answer_0" -> "A";
                case "answer_1" -> "B";
                case "answer_2" -> "C";
                default -> "";
            };
            case "coop" -> switch (action) {
                case "guess_ruby" -> "R";
                case "guess_jade" -> "J";
                case "guess_azure" -> "A";
                default -> "";
            };
            default -> "";
        };
        if (!token.isEmpty()) {
            int color = switch (action) {
                case "red", "guess_ruby" -> 0xFFD66E79;
                case "black" -> 0xFF111A24;
                case "guess_jade" -> 0xFF71C7A2;
                case "guess_azure" -> 0xFF78B9EC;
                default -> accent(game);
            };
            context.fill(x, y, x + 18, y + 18, GOLD);
            context.fill(x + 1, y + 1, x + 17, y + 17, color);
            context.text(font, token, x + 9 - font.width(token) / 2, y + 5,
                    color == 0xFF111A24 ? INK : 0xFF10242C, false);
            return;
        }
        drawButtonMark(context, game, x, y);
    }

    private void drawButtonMark(GuiGraphicsExtractor context, String game, int x, int y) {
        if (!hasArt(game)) return;
        Identifier texture = Identifier.fromNamespaceAndPath("gems",
                "textures/gui/casino/tables/" + game + "_mark.png");
        context.blit(RenderPipelines.GUI_TEXTURED, texture, x, y,
                0.0F, 0.0F, 18, 18, 20, 20, 20, 20);
    }

    private static boolean hasArt(String game) {
        return game.equals("rps") || game.equals("cards") || game.equals("wheel")
                || game.equals("reels") || game.equals("trivia") || game.equals("coop");
    }

    private void drawSceneArt(GuiGraphicsExtractor context, int x, int y, int width, int height) {
        if (!hasArt(view.gameId()) || width < 160 || height < 16) return;
        if (height < 28) {
            int center = x + width / 2;
            context.fill(x + 18, y + height / 2, center - 18, y + height / 2 + 1, BORDER);
            context.fill(center + 18, y + height / 2, x + width - 18, y + height / 2 + 1, BORDER);
            drawButtonMark(context, view.gameId(), center - 9, y + (height - 18) / 2);
            return;
        }
        Identifier texture = Identifier.fromNamespaceAndPath("gems",
                "textures/gui/casino/tables/" + view.gameId() + "_scene.png");
        int artWidth = Math.min(width, height * 320 / 56);
        context.blit(RenderPipelines.GUI_TEXTURED, texture, x + (width - artWidth) / 2, y,
                0.0F, 0.0F, artWidth, height, 320, 56, 320, 56);
    }

    private void drawRoundStatus(GuiGraphicsExtractor context, int accent) {
        boolean free = view.title().startsWith("Free:");
        boolean signalTable = view.gameId().equals("coop");
        String state = switch (view.phase()) {
            case "PREVIEW" -> signalTable ? "READY" : "REVIEW";
            case "ROUND", "COOP_PLAY" -> "PLAYING";
            case "COOP_WAIT" -> "WAITING";
            case "RESULT" -> "FINISHED";
            default -> "TABLE";
        };
        String entryLabel = view.phase().equals("COOP_WAIT") ? "NEEDED"
                : isLiveRound() ? "IN PLAY" : signalTable ? "STAKE" : "ENTRY";
        String entry = free ? "FREE" : view.stake() + " EMERALDS";
        String next = actionPending ? "SENDING..." : switch (view.phase()) {
            case "PREVIEW" -> signalTable ? "JOIN" : "YOUR CHOICE";
            case "ROUND" -> "YOUR MOVE";
            case "COOP_WAIT" -> signalTable ? "PARTNER" : "2ND PLAYER";
            case "COOP_PLAY" -> view.choices().isEmpty() ? "PARTNER" : "YOUR GUESS";
            case "RESULT" -> signalTable ? "PLAY AGAIN" : "ROUND DONE";
            default -> "TABLE";
        };
        GemsUiTheme.card(context, left + 14, top + 36, left + panelWidth - 14,
                top + 63, accent);
        int cellWidth = (panelWidth - 28) / 3;
        int cellLeft = left + 14;
        drawStatusCell(context, cellLeft, cellWidth, "STATUS", state, accent);
        drawStatusCell(context, cellLeft + cellWidth, cellWidth, entryLabel, entry, GOLD);
        drawStatusCell(context, cellLeft + cellWidth * 2, panelWidth - 28 - cellWidth * 2,
                signalTable ? "NEXT" : "TURN", next, accent);
        if (panelWidth >= 340) {
            drawButtonMark(context, view.gameId(), cellLeft + cellWidth - 28, top + 41);
        }
        context.fill(cellLeft + cellWidth, top + 41, cellLeft + cellWidth + 1, top + 58, BORDER);
        context.fill(cellLeft + cellWidth * 2, top + 41, cellLeft + cellWidth * 2 + 1, top + 58, BORDER);
    }

    private void drawStatusCell(GuiGraphicsExtractor context, int x, int width,
                                String label, String value, int color) {
        drawTrimmed(context, label, x + 9, top + 40, width - 17, MUTED);
        drawTrimmed(context, value, x + 9, top + 51, width - 17, color);
    }

    private void drawLobby(GuiGraphicsExtractor context, int accent) {
        boolean tournament = view.phase().equals("TOURNAMENT_LOBBY");
        context.text(font, tournament ? "TODAY'S FEATURED TABLES" : "CHOOSE A TABLE", left + 18, top + 45, accent, false);
        if (tournament) {
            drawWrapped(context, view.rules(), left + 18, top + 60,
                    panelWidth - 36, INK, buttonTop - 4);
        } else {
            drawTrimmed(context, "Read the rules, then choose a table.",
                    left + 18, top + 60, panelWidth - 36, INK);
        }
        int rows = (view.choices().size() + 1) / 2;
        int step = panelHeight < 260 ? 25 : 31;
        int height = panelHeight < 260 ? 21 : 26;
        int afterChoices = buttonTop + (rows - 1) * step + height + 6;
        int available = top + panelHeight - 39 - afterChoices;
        if (available >= 28) {
            context.fill(left + 16, afterChoices, left + panelWidth - 16, afterChoices + 1, 0xFF426267);
            context.text(font, tournament ? "WORLD BOARD" : "AT THE TABLE", left + 18, afterChoices + 9, accent, false);
            drawWrapped(context,
                    tournament ? view.board().isEmpty() ? "Team up or finish three solo tables."
                            : view.board().getFirst()
                            : "Play solo or try Signal Table together. Your gem and story progress are safe.",
                    left + 18, afterChoices + 24, panelWidth - 36, MUTED,
                    top + panelHeight - 42);
        } else if (tournament && !view.board().isEmpty()
                && afterChoices + 9 <= top + panelHeight - 35) {
            drawTrimmed(context, view.board().getFirst(), left + 18, afterChoices,
                    panelWidth - 36, MUTED);
        }
    }

    private void drawRules(GuiGraphicsExtractor context, int accent) {
        int bottom = rulesOpen ? top + panelHeight - 43 : buttonTop - 8;
        int y = top + (isPreview() ? 65 : 73);
        if (bottom - y >= 112) {
            drawSceneArt(context, left + 16, y, panelWidth - 32, 42);
            y += 50;
        }
        boolean free = view.title().startsWith("Free:");
        context.text(font, free ? "FREE ROUND RULES" : view.gameId().equals("coop") ? "HOW TO PLAY" : "RULES & RETURNS",
                left + 18, y, accent, false);
        y += 12;
        y = drawWrapped(context, view.rules(), left + 18, y,
                panelWidth - 36, INK, bottom);
        if (isPreview()) {
            y += 10;
            if (!view.message().startsWith("Read the rules")) {
                y = drawWrapped(context, view.message(), left + 18, y,
                        panelWidth - 36, accent, bottom) + 8;
            }
            if (y + 17 <= bottom) {
                context.fill(left + 18, y, left + panelWidth - 18, y + 1, 0xFF426267);
                drawWrapped(context, free ? "No emeralds change hands. After 10 idle minutes, the round ends without a win."
                                : view.gameId().equals("coop")
                                ? "A disconnect or 10 idle minutes returns both players' payments."
                                : "Payouts include the emeralds you paid. After 10 idle minutes, a solo round loses them.",
                        left + 18, y + 8, panelWidth - 36, MUTED, bottom);
            }
        }
    }

    private int drawWrapped(GuiGraphicsExtractor context, String value, int x, int y,
                            int maxWidth, int color, int bottom) {
        for (FormattedCharSequence line : font.split(Component.literal(value), maxWidth)) {
            if (y + 9 > bottom) break;
            context.text(font, line, x, y, color, false);
            y += 11;
        }
        return y;
    }

    private void drawBoard(GuiGraphicsExtractor context, List<String> source, int boardTop,
                           int bottom, int accent, boolean sceneAboveBoard) {
        if (view.phase().equals("RESULT") && !source.isEmpty()) {
            if (view.gameId().equals("wheel") && drawWheelResult(context, source.getFirst(), boardTop, bottom)) return;
            if (view.gameId().equals("reels") && drawReelsResult(context, source.getFirst(), boardTop, bottom)) return;
        }
        int nextY = boardTop;
        if (!view.playerCards().isEmpty()) {
            // Keep the dealer's visible card on screen at compact GUI scales.
            boolean expandedCards = panelHeight >= 220 && panelWidth >= 380
                    && boardTop + 43 + 38 <= bottom;
            int rowHeight = expandedCards ? 38 : 26;
            int rowStep = expandedCards ? 43 : 29;
            if (boardTop + rowHeight <= bottom) drawCardRow(context, "YOU", view.playerCards(),
                    score(source, 0), boardTop, expandedCards);
            if (boardTop + rowStep + rowHeight <= bottom) drawCardRow(context, "DEALER", view.dealerCards(),
                    score(source, 1), boardTop + rowStep, expandedCards);
            nextY = boardTop + rowStep + rowHeight + 2;
        } else {
            int rowHeight = sceneAboveBoard ? 24 : panelHeight < 250 ? 22 : 31;
            int maxRows = Math.max(0, (bottom - boardTop) / rowHeight);
            int tileWidth = panelWidth - 32;
            List<String> board = source;
            if (view.gameId().equals("rps") && board.size() > maxRows && maxRows > 0) {
                List<String> latest = new ArrayList<>();
                latest.add(board.getFirst());
                if (maxRows > 1) latest.addAll(board.subList(
                        Math.max(1, board.size() - maxRows + 1), board.size()));
                board = latest;
            }
            int visibleRows = Math.min(board.size(), maxRows);
            for (int i = 0; i < visibleRows; i++) {
                int x = left + 16;
                int y = boardTop + i * rowHeight;
                int tileHeight = rowHeight - 4;
                context.fill(x, y, x + tileWidth, y + tileHeight, TILE);
                context.fill(x, y, x + 2, y + tileHeight, accent);
                if (view.gameId().equals("reels") && i == 0 && tileHeight >= 17
                        && drawReels(context, board.get(i), x, y, tileWidth, tileHeight)) continue;
                int textX = x + 8;
                if (tileHeight >= 19 && !view.gameId().equals("lobby")) {
                    drawTableMark(context, view.gameId(), board.get(i), i, x + 8, y + 3);
                    textX += 20;
                }
                if (tileHeight >= 25) {
                    drawWrapped(context, board.get(i), textX, y + 3,
                            tileWidth - (textX - x) - 6, INK, y + tileHeight - 2);
                } else {
                    drawTrimmed(context, board.get(i), textX, y + Math.max(1, (tileHeight - 8) / 2),
                            tileWidth - (textX - x) - 6, INK);
                }
            }
            nextY = boardTop + visibleRows * rowHeight + 2;
        }
        if (isLiveRound() && !sceneAboveBoard && nextY + 20 <= bottom) {
            drawSceneArt(context, left + 16, nextY, panelWidth - 32,
                    Math.min(54, bottom - nextY));
        }
    }

    private static String score(List<String> board, int index) {
        if (index >= board.size()) return "";
        String line = board.get(index);
        int start = line.lastIndexOf('(');
        int end = line.lastIndexOf(')');
        return start >= 0 && end > start ? line.substring(start, end + 1) : "(?)";
    }

    private void drawTrimmed(GuiGraphicsExtractor context, String text, int x, int y, int maxWidth, int color) {
        String visible = font.plainSubstrByWidth(text, maxWidth);
        if (visible.length() < text.length()) {
            visible = font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("..."))) + "...";
        }
        context.text(font, visible, x, y, color, false);
    }

    private void drawCardRow(GuiGraphicsExtractor context, String label, List<String> cards,
                             String score, int y, boolean expanded) {
        int rowHeight = expanded ? 38 : 26;
        int cardSize = expanded ? 36 : 24;
        int cardStep = expanded ? 35 : 27;
        int textY = y + (rowHeight - 8) / 2;
        context.fill(left + 16, y, left + panelWidth - 16, y + rowHeight, 0xFF183039);
        context.fill(left + 16, y, left + 18, y + rowHeight, GOLD);
        context.text(font, label, left + 23, textY, GOLD, false);
        int x = left + (expanded ? 83 : 87);
        int maxCards = Math.max(1, Math.min(cards.size(),
                (panelWidth - (expanded ? 160 : 145)) / cardStep));
        for (int i = 0; i < maxCards; i++) {
            String card = cards.get(i);
            if (!CARD_NAME.matcher(card).matches()) continue;
            Identifier texture = Identifier.fromNamespaceAndPath("gems", "textures/gui/casino/cards/" + card + ".png");
            context.blit(RenderPipelines.GUI_TEXTURED, texture,
                    x + i * cardStep, y + (rowHeight - cardSize) / 2,
                    0.0F, 0.0F, cardSize, cardSize, 32, 32, 32, 32);
        }
        if (cards.size() > maxCards) {
            context.text(font, "+" + (cards.size() - maxCards),
                    x + maxCards * cardStep, textY, MUTED, false);
        }
        context.text(font, score,
                left + panelWidth - 22 - font.width(score), textY, INK, false);
    }

    private boolean drawReels(GuiGraphicsExtractor context, String line, int x, int y, int width, int height) {
        String[] symbols = reelSymbols(line);
        if (symbols == null) return false;
        int gap = 5;
        int cellWidth = (width - 16 - gap * 2) / 3;
        for (int i = 0; i < 3; i++) {
            int cellX = x + 8 + i * (cellWidth + gap);
            context.fill(cellX, y + 2, cellX + cellWidth, y + height - 2, 0xFF101B27);
            context.fill(cellX, y + 2, cellX + cellWidth, y + 3, 0xFFE8CA83);
            int symbolSize = Math.min(18, height - 4);
            drawReelSymbol(context, symbols[i], cellX + 4, y + (height - symbolSize) / 2, symbolSize);
            drawTrimmed(context, symbols[i], cellX + symbolSize + 7, y + Math.max(3, (height - 8) / 2),
                    cellWidth - symbolSize - 11, INK);
        }
        return true;
    }

    private static String[] reelSymbols(String line) {
        if (!line.startsWith("[ ") || !line.endsWith(" ]")) return null;
        String[] symbols = line.substring(2, line.length() - 2).split(" \\] \\[ ");
        if (symbols.length != 3) return null;
        for (String symbol : symbols) {
            if (!symbol.equals("Seven") && !symbol.equals("Diamond")
                    && !symbol.equals("Bell") && !symbol.equals("Clover")) return null;
        }
        return symbols;
    }

    private double revealProgress() {
        // A compact panel shows the authoritative result immediately, without visual delay.
        if (reducedMotion || panelWidth < 320 || revealStartedAt == 0L) return 1.0;
        long elapsed = Math.max(0L, System.nanoTime() - revealStartedAt);
        return Math.min(1.0, elapsed / (double) RESULT_REVEAL_NANOS);
    }

    private boolean drawWheelResult(GuiGraphicsExtractor context, String line, int y, int bottom) {
        Matcher match = WHEEL_RESULT.matcher(line);
        if (!match.matches() || bottom - y < 58) return false;
        int number = Integer.parseInt(match.group(1));
        if (number > 36) return false;
        int x = left + 16;
        int width = panelWidth - 32;
        int height = Math.min(82, bottom - y);
        int centerX = x + 45;
        int centerY = y + height / 2;
        double progress = revealProgress();
        double ease = 1.0 - Math.pow(1.0 - progress, 3.0);
        int selected = progress >= 1.0 ? number : (int) Math.floor((74 + number) * ease) % 37;
        context.fill(x, y, x + width, y + height, 0xFF162D36);
        context.fill(x, y, x + 3, y + height, accent("wheel"));
        for (int slot = 0; slot < 37; slot++) {
            double angle = 2.0 * Math.PI * slot / 37.0 - Math.PI / 2.0;
            int dotX = centerX + (int) Math.round(25.0 * Math.cos(angle));
            int dotY = centerY + (int) Math.round(25.0 * Math.sin(angle));
            int color = slot == selected ? GOLD : 0xFF55717A;
            context.fill(dotX - (slot == selected ? 3 : 1), dotY - (slot == selected ? 3 : 1),
                    dotX + (slot == selected ? 4 : 2), dotY + (slot == selected ? 4 : 2), color);
        }
        String center = progress >= 1.0 ? Integer.toString(number) : "?";
        context.text(font, center, centerX - font.width(center) / 2, centerY - 4, INK, false);
        drawTrimmed(context, progress >= 1.0
                        ? number + " " + match.group(2).toUpperCase(Locale.ROOT) : "WHEEL TURNING",
                x + 89, y + 15, width - 103, INK);
        drawTrimmed(context, "YOUR CALL: " + match.group(3).toUpperCase(Locale.ROOT),
                x + 89, y + 29, width - 103, MUTED);
        int trackEnd = x + width - 14;
        context.fill(x + 89, y + height - 15, trackEnd, y + height - 13, BORDER);
        context.fill(x + 89, y + height - 15,
                x + 89 + (int) Math.round((trackEnd - x - 89) * progress), y + height - 13, GOLD);
        return true;
    }

    private boolean drawReelsResult(GuiGraphicsExtractor context, String line, int y, int bottom) {
        String[] symbols = reelSymbols(line);
        if (symbols == null || bottom - y < 58) return false;
        int x = left + 16;
        int width = panelWidth - 32;
        int height = Math.min(82, bottom - y);
        double progress = revealProgress();
        int shown = progress >= 1.0 ? 3 : Math.min(3, (int) (progress * 4.0));
        int gap = 6;
        int cellWidth = (width - 16 - gap * 2) / 3;
        context.fill(x, y, x + width, y + height, 0xFF162D36);
        context.fill(x, y, x + 3, y + height, accent("reels"));
        for (int i = 0; i < 3; i++) {
            int cellX = x + 8 + i * (cellWidth + gap);
            context.fill(cellX, y + 6, cellX + cellWidth, y + height - 23, 0xFF101B27);
            context.fill(cellX, y + 6, cellX + cellWidth, y + 8, i < shown ? GOLD : BORDER);
            if (i < shown) {
                int symbolSize = Math.min(42, height - 31);
                drawReelSymbol(context, symbols[i], cellX + 6, y + 8, symbolSize);
                drawTrimmed(context, symbols[i], cellX + symbolSize + 10,
                        y + Math.max(12, (height - 23 - 8) / 2), cellWidth - symbolSize - 15, INK);
            } else {
                context.text(font, "?", cellX + cellWidth / 2 - 3, y + 19, MUTED, false);
            }
        }
        String finish = symbols[0].equals(symbols[1]) && symbols[1].equals(symbols[2])
                ? "THREE MATCH" : symbols[0].equals(symbols[1]) || symbols[0].equals(symbols[2])
                || symbols[1].equals(symbols[2]) ? "PAIR" : "NO MATCH";
        drawTrimmed(context, shown == 3 ? finish : "REVEALING " + shown + "/3",
                x + 12, y + height - 17, width - 24, shown == 3 ? GOLD : MUTED);
        return true;
    }

    private void drawReelSymbol(GuiGraphicsExtractor context, String symbol, int x, int y, int size) {
        String sprite = switch (symbol) {
            case "Seven" -> "seven";
            case "Diamond" -> "diamond";
            case "Bell" -> "bell";
            case "Clover" -> "clover";
            default -> null;
        };
        if (sprite == null) return;
        Identifier texture = Identifier.fromNamespaceAndPath("gems",
                "textures/gui/casino/reels/" + sprite + ".png");
        context.blit(RenderPipelines.GUI_TEXTURED, texture, x, y,
                0.0F, 0.0F, size, size, 128, 128, 128, 128);
    }

    private void drawTableMark(GuiGraphicsExtractor context, String game, String line, int row, int x, int y) {
        switch (game) {
            case "rps" -> {
                context.fill(x, y + 1, x + 13, y + 13, 0xFF5B3432);
                context.text(font, row == 0 ? "2" : "R", x + 4, y + 3, INK, false);
            }
            case "cards" -> {
                context.fill(x + 2, y, x + 14, y + 13, 0xFFF3EBD6);
                context.fill(x + 2, y, x + 14, y + 2, 0xFF85C8AD);
                context.text(font, row == 0 ? "A" : "D", x + 5, y + 3, 0xFF172632, false);
            }
            case "wheel" -> {
                context.fill(x, y + 2, x + 6, y + 12, 0xFFB84B5A);
                context.fill(x + 6, y + 2, x + 12, y + 12, 0xFF141820);
                context.fill(x + 4, y + 5, x + 8, y + 9, 0xFF62A978);
            }
            case "trivia" -> {
                context.fill(x, y + 1, x + 13, y + 13, 0xFF374E70);
                context.text(font, "?", x + 4, y + 3, INK, false);
            }
            case "coop" -> {
                int color = line.contains("Ruby") ? 0xFFD66E79
                        : line.contains("Jade") ? 0xFF71C7A2
                        : line.contains("Azure") ? 0xFF78B9EC : 0xFFB9A5DF;
                context.fill(x, y + 3, x + 6, y + 11, color);
                context.fill(x + 7, y + 3, x + 13, y + 11, color);
                context.fill(x + 5, y + 6, x + 8, y + 8, INK);
            }
            default -> {
            }
        }
    }

    private static int accent(String id) {
        return switch (id) {
            case "rps" -> 0xFFE69C84;
            case "cards" -> 0xFF85C8AD;
            case "wheel" -> 0xFFE0A0B5;
            case "reels" -> 0xFFE8CA83;
            case "trivia" -> 0xFF9CB7E8;
            case "coop" -> 0xFFB9A5DF;
            default -> GOLD;
        };
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xB0071019);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
