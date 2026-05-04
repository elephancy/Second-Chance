package com.feel.gems.gametest.adventure;

import com.feel.gems.client.ClientGemState;
import com.feel.gems.client.modmenu.GemsConfigScreen;
import com.feel.gems.client.screen.GuidebookScreen;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;

/** Opt-in visual and input acceptance against a fresh packaged 26.3 install. */
@SuppressWarnings("UnstableApiUsage")
public final class PackagedUiAcceptanceClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        String variant = System.getProperty("gems.uiAcceptance.variant", "");
        String scale = System.getProperty("gems.uiAcceptance.scale", "");
        require(variant.equals("gems") || variant.equals("both"), "Invalid UI acceptance variant");
        require(scale.equals("2") || scale.equals("3"), "Invalid UI acceptance scale");
        String label = variant + "-scale" + scale;
        System.out.println("UI_ACCEPTANCE_PHASE " + label + " start");
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            context.setScreen(GuidebookScreen::new);
            context.waitTicks(3);
            context.runOnClient(client -> {
                Screen screen = client.gui.screen();
                require(screen instanceof GuidebookScreen, "Guide did not open");
                require(button(screen, "Learn the basics"), "Home lacks basics route");
                require(button(screen, "Settings"), "Home lacks direct Settings route");
                require(button(screen, "Check controls"), "Home lacks controls route");
                require(button(screen, "Find a place") == variant.equals("both"),
                        "Campaign route presence does not match installed jars");
                require(button(screen, ClientGemState.isInitialized() ? "See my gem" : "Browse gems"),
                        "Current gem state is not honestly represented on Home");
                int expectedWidth = scale.equals("2") ? 640 : 427;
                require(Math.abs(screen.width - expectedWidth) <= 1,
                        "Wrong scaled width: " + screen.width + " expected " + expectedWidth);
            });
            context.takeScreenshot("ui-acceptance-" + label + "-guide-home");
            System.out.println("UI_ACCEPTANCE_PHASE " + label + " guide-home");

            // clickScreenButton drives the button's actual pointer target, not its callback.
            context.clickScreenButton("Settings");
            context.waitTicks(2);
            context.runOnClient(client -> {
                Screen screen = client.gui.screen();
                require(screen instanceof GemsConfigScreen, "Settings did not open from guide");
                require(button(screen, "General") && button(screen, "Elemental"),
                        "World settings categories are missing in local play");
                require(button(screen, "Done"), "Settings has no return control");
            });
            context.takeScreenshot("ui-acceptance-" + label + "-settings-general");
            if (scale.equals("3")) context.clickScreenButton("<");
            else context.clickScreenButton("Client");
            context.runOnClient(client -> require(count(client.gui.screen(), CycleButton.class) >= 2,
                    "Client settings lack passive and control-mode choices"));
            context.takeScreenshot("ui-acceptance-" + label + "-settings-controls");
            context.clickScreenButton("Elemental");
            if (scale.equals("3")) context.clickScreenButton(">");
            else context.clickScreenButton("Fire");
            context.runOnClient(client -> require(count(client.gui.screen(), EditBox.class)
                    >= (scale.equals("3") ? 2 : 3),
                    "Fire settings lack visible focused world controls"));
            context.takeScreenshot("ui-acceptance-" + label + "-settings-fire");
            if (scale.equals("3")) {
                context.runOnClient(client -> client.gui.screen().mouseScrolled(200, 140, 0, -2));
                context.runOnClient(client -> require(client.gui.screen().children().stream()
                        .filter(EditBox.class::isInstance).map(EditBox.class::cast)
                        .anyMatch(field -> field.getMessage().getString().equals("Fireball charge (s)")),
                        "Compact settings cannot reach the third Fire control"));
            }
            context.clickScreenButton("Done");
            context.runOnClient(client -> require(client.gui.screen() instanceof GuidebookScreen
                    && button(client.gui.screen(), "Learn the basics"),
                    "Done did not restore guide Home"));

            if (variant.equals("both")) {
                context.clickScreenButton("Find a place");
                context.waitTicks(2);
                context.runOnClient(client -> require(buttonStartingWith(client.gui.screen(), "Jump: Quick start"),
                        "Campaign path chapter is missing"));
                context.takeScreenshot("ui-acceptance-" + label + "-campaign-path");
                AtomicReference<String> homeLabel = new AtomicReference<>();
                context.runOnClient(client -> homeLabel.set(client.gui.screen().children().stream()
                        .filter(Button.class::isInstance).map(Button.class::cast)
                        .filter(button -> button.visible && button.getMessage().getString().startsWith("Home"))
                        .map(button -> button.getMessage().getString()).findFirst().orElse(null)));
                require(homeLabel.get() != null, "Campaign chapter has no Home route");
                context.clickScreenButton(homeLabel.get());
            }
            context.runOnClient(client -> {
                Screen screen = client.gui.screen();
                require(screen instanceof GuidebookScreen, "Guide lost focus after return");
                screen.keyPressed(new KeyEvent(InputConstants.KEY_F, 0, InputConstants.MOD_CONTROL));
            });
            context.waitTicks(1);
            context.runOnClient(client -> {
                EditBox search = client.gui.screen().children().stream()
                        .filter(EditBox.class::isInstance).map(EditBox.class::cast)
                        .findFirst().orElseThrow(() -> new IllegalStateException("Ctrl+F lacks search field"));
                search.setValue("Astral Daggers");
            });
            context.waitTicks(2);
            context.runOnClient(client -> require(button(client.gui.screen(), "Gems / Astra Gem"),
                    "Search lacks the matching gem section"));
            context.takeScreenshot("ui-acceptance-" + label + "-search");
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            context.waitTicks(1);
            context.runOnClient(client -> require(button(client.gui.screen(), "Learn the basics"),
                    "Escape did not restore guide Home"));
            context.setScreen(() -> null);
        }
        System.out.println("UI_ACCEPTANCE_PASS " + label
                + " navigation=pointer keyboard=CtrlF,Escape return=guide screenshots=5-or-6");
    }

    private static boolean button(Screen screen, String text) {
        return screen != null && screen.children().stream().filter(Button.class::isInstance)
                .map(Button.class::cast)
                .anyMatch(button -> button.visible && text.equals(button.getMessage().getString()));
    }

    private static boolean buttonStartingWith(Screen screen, String prefix) {
        return screen != null && screen.children().stream().filter(Button.class::isInstance)
                .map(Button.class::cast)
                .anyMatch(button -> button.visible && button.getMessage().getString().startsWith(prefix));
    }

    private static <T extends GuiEventListener> long count(Screen screen, Class<T> type) {
        return screen.children().stream().filter(type::isInstance).count();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
