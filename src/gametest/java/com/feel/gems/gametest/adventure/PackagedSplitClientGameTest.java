package com.feel.gems.gametest.adventure;

import com.feel.gems.client.screen.GuidebookScreen;
import com.mojang.blaze3d.platform.InputConstants;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;

/** Test-only keyboard and content check of an installed production jar pair. */
@SuppressWarnings("UnstableApiUsage")
public final class PackagedSplitClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        String mode = PackagedSplitServerProbe.verifyMode();
        System.out.println("PACKAGED_SPLIT_CLIENT_PHASE mode=" + mode + " phase=start");
        boolean both = "both".equals(mode);
        boolean campaignContent;
        try (InputStream guide = getClass().getClassLoader()
                .getResourceAsStream("assets/gems/guidebook/adventure.md")) {
            campaignContent = guide != null && guide.read() >= 0;
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Could not read campaign guidebook", e);
        }
        require(campaignContent == both, "Campaign guide content presence does not match " + mode);
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            System.out.println("PACKAGED_SPLIT_CLIENT_PHASE mode=" + mode + " phase=chunks-rendered");
            context.setScreen(GuidebookScreen::new);
            context.waitTicks(4);
            System.out.println("PACKAGED_SPLIT_CLIENT_PHASE mode=" + mode + " phase=guide-open");
            context.runOnClient(client -> {
                require(client.gui.screen() instanceof GuidebookScreen, "Guide did not open");
                require(button(client.gui.screen().children(), "Learn the basics"),
                        "Core guide home is missing");
                require(button(client.gui.screen().children(), "Find a place") == both,
                        "Campaign guide home presence does not match " + mode);
            });
            if (both) {
                context.clickScreenButton("Find a place");
                context.waitTicks(2);
                context.runOnClient(client -> require(
                        buttonStartingWith(client.gui.screen().children(), "Jump: Quick start"),
                        "Campaign route chapter is missing"));
                AtomicReference<String> homeLabel = new AtomicReference<>();
                context.runOnClient(client -> homeLabel.set(client.gui.screen().children().stream()
                        .filter(Button.class::isInstance).map(Button.class::cast)
                        .filter(button -> button.visible && button.getMessage().getString().startsWith("Home"))
                        .map(button -> button.getMessage().getString()).findFirst().orElse(null)));
                require(homeLabel.get() != null, "Guide chapter has no Home button");
                context.clickScreenButton(homeLabel.get());
            }
            context.clickScreenButton("Search");
            context.runOnClient(client -> client.gui.screen().children().stream()
                    .filter(EditBox.class::isInstance).map(EditBox.class::cast)
                    .findFirst().orElseThrow().setValue("Astral Daggers"));
            context.waitTicks(2);
            context.runOnClient(client -> require(
                    button(client.gui.screen().children(), "Gems / Astra Gem"),
                    "Core guide search result is missing"));
            System.out.println("PACKAGED_SPLIT_CLIENT_PHASE mode=" + mode + " phase=search-result");
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            context.waitTicks(1);
            context.runOnClient(client -> require(
                    button(client.gui.screen().children(), "Learn the basics"),
                    "Escape did not return to guide home"));
            System.out.println("PACKAGED_SPLIT_CLIENT_PHASE mode=" + mode + " phase=escape-home");
            // Fabric's synthetic key input supplies zero modifier bits even
            // while Control is held. Dispatch a 26.3 KeyEvent with the real
            // modifier mask to verify the screen shortcut handler itself.
            context.runOnClient(client -> client.gui.screen().keyPressed(
                    new KeyEvent(InputConstants.KEY_F, 0, InputConstants.MOD_CONTROL)));
            context.waitTicks(1);
            context.runOnClient(client -> require(client.gui.screen().children().stream()
                    .anyMatch(EditBox.class::isInstance), "Ctrl+F did not open guide search"));
            System.out.println("PACKAGED_SPLIT_CLIENT_PHASE mode=" + mode + " phase=ctrl-f-search");
            context.setScreen(() -> null);
        }
        System.out.println("PACKAGED_SPLIT_CLIENT_PASS mode=" + mode
                + " keyboard=Escape shortcutHandler=CtrlF");
    }

    private static boolean button(java.util.List<? extends net.minecraft.client.gui.components.events.GuiEventListener> children,
                                  String text) {
        return children.stream().filter(Button.class::isInstance).map(Button.class::cast)
                .anyMatch(button -> button.visible && text.equals(button.getMessage().getString()));
    }

    private static boolean buttonStartingWith(
            java.util.List<? extends net.minecraft.client.gui.components.events.GuiEventListener> children,
            String prefix) {
        return children.stream().filter(Button.class::isInstance).map(Button.class::cast)
                .anyMatch(button -> button.visible && button.getMessage().getString().startsWith(prefix));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
