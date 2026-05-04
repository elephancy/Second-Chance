package com.feel.gems.gametest.adventure;

import com.feel.gems.client.GemsClientConfigManager;
import com.feel.gems.client.modmenu.GemsConfigScreen;
import com.feel.gems.config.GemsBalance;
import com.feel.gems.config.GemsDisables;
import com.feel.gems.config.GemsDisablesConfigManager;
import com.feel.gems.core.GemId;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;

/** Opt-in test against actual client and integrated-server config files. */
@SuppressWarnings("UnstableApiUsage")
public final class ConfigSaveClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        if (!Boolean.getBoolean("gems.configSaveFixture")) return;
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            Path directory = FabricLoader.getInstance().getConfigDir().resolve("gems");
            Path balance = directory.resolve("balance.json");
            Path effective = directory.resolve("balance.effective.json");
            Path disables = directory.resolve("disables.json");
            Path client = directory.resolve("client.json");
            byte[] originalBalance = readIfPresent(balance);
            byte[] originalEffective = readIfPresent(effective);
            byte[] originalDisables = readIfPresent(disables);
            byte[] originalClient = readIfPresent(client);
            try {
                JsonObject balanceJson = originalBalance == null ? new JsonObject()
                        : JsonParser.parseString(Files.readString(balance)).getAsJsonObject();
                JsonObject fire = balanceJson.has("fire") && balanceJson.get("fire").isJsonObject()
                        ? balanceJson.getAsJsonObject("fire") : new JsonObject();
                fire.addProperty("heatHazeDurationSeconds", 77);
                fire.addProperty("futureFireControl", "preserve-me");
                balanceJson.add("fire", fire);
                JsonObject futureSection = new JsonObject();
                futureSection.addProperty("newRule", 7);
                balanceJson.add("futureSection", futureSection);
                Files.writeString(balance, balanceJson.toString());

                context.setScreen(() -> new GemsConfigScreen(null));
                context.waitTicks(2);
                context.clickScreenButton("Elemental");
                context.clickScreenButton("Fire");
                context.runOnClient(mc -> firstField(mc.gui.screen()).setValue("61"));
                context.clickScreenButton("Save + Reload");
                context.waitTicks(30);

                JsonObject saved = JsonParser.parseString(Files.readString(balance)).getAsJsonObject();
                check(saved.getAsJsonObject("fire").get("cosyCampfireCooldownSeconds").getAsInt() == 61,
                        "Fire cooldown was not saved through the screen");
                check(saved.getAsJsonObject("fire").get("heatHazeDurationSeconds").getAsInt() == 77,
                        "Unedited advanced Fire value was lost");
                check("preserve-me".equals(saved.getAsJsonObject("fire").get("futureFireControl").getAsString()),
                        "Unknown nested Fire key was lost");
                check(saved.getAsJsonObject("futureSection").get("newRule").getAsInt() == 7,
                        "Unknown top-level section was lost");
                int liveTicks = world.getServer().computeOnServer(server ->
                        GemsBalance.v().fire().cosyCampfireCooldownTicks());
                check(liveTicks == 61 * GemsBalance.TICKS_PER_SECOND,
                        "Save + Reload did not update the integrated server: " + liveTicks);
                context.clickScreenButton("Reload from disk");
                context.runOnClient(mc -> check("61".equals(firstField(mc.gui.screen()).getValue()),
                        "Reload from disk did not show the saved Fire cooldown"));
                System.out.println("[config-save] PASS: Fire UI save, unknown keys, and live server reload");

                context.clickScreenButton("More tools");
                context.clickScreenButton("Dump effective");
                context.waitTicks(10);
                context.takeScreenshot("config-effective-dump-feedback");
                check(Files.exists(effective), "Dump effective did not create its file");
                JsonObject dumped = JsonParser.parseString(Files.readString(effective)).getAsJsonObject();
                check(dumped.getAsJsonObject("fire").get("cosyCampfireCooldownSeconds").getAsInt() == 61,
                        "Effective dump did not contain the live Fire cooldown");
                System.out.println("[config-save] PASS: local effective balance dump");
                context.clickScreenButton("Back");

                Files.writeString(disables, "{\"futureDisableRule\":17}");
                context.clickScreenButton("General");
                context.runOnClient(mc -> mc.gui.screen().mouseScrolled(30, 120, 0, -50));
                context.clickScreenButton("Disables");
                context.runOnClient(mc -> firstField(mc.gui.screen()).setValue("fire"));
                context.clickScreenButton("Save + Reload");
                context.waitTicks(10);
                JsonObject savedDisables = JsonParser.parseString(Files.readString(disables)).getAsJsonObject();
                check(savedDisables.getAsJsonArray("disabledGems").get(0).getAsString().equals("fire")
                                && savedDisables.get("futureDisableRule").getAsInt() == 17,
                        "Disables save lost the edit or an unknown key");
                check(world.getServer().computeOnServer(server -> GemsDisables.isGemDisabled(GemId.FIRE)),
                        "Save + Reload did not apply the Fire disable to the local server");
                System.out.println("[config-save] PASS: Disables UI save and live server reload");
                context.clickScreenButton("Elemental");
                context.clickScreenButton("Fire");

                Files.writeString(balance, "{broken");
                context.clickScreenButton("Reload from disk");
                context.runOnClient(mc -> check(!button(mc.gui.screen(), "Save").active,
                        "Malformed balance.json left Save enabled"));
                check("{broken".equals(Files.readString(balance)), "Malformed balance.json was overwritten");
                System.out.println("[config-save] PASS: malformed balance file kept and Save blocked");

                JsonObject clientJson = new JsonObject();
                clientJson.addProperty("controlMode", "CHORD");
                clientJson.addProperty("passivesEnabled", true);
                clientJson.addProperty("futureClientRule", 12);
                Files.writeString(client, clientJson.toString());
                GemsClientConfigManager.reloadFromDisk();
                var changedClient = GemsClientConfigManager.copyForUi(GemsClientConfigManager.config());
                changedClient.passivesEnabled = false;
                check(GemsClientConfigManager.save(changedClient), "Valid client.json would not save");
                JsonObject savedClient = JsonParser.parseString(Files.readString(client)).getAsJsonObject();
                check(!savedClient.get("passivesEnabled").getAsBoolean()
                                && savedClient.get("futureClientRule").getAsInt() == 12,
                        "Client save lost an unknown key or the edited toggle");
                Files.writeString(client, "{broken");
                GemsClientConfigManager.reloadFromDisk();
                context.clickScreenButton("Reload from disk");
                context.clickScreenButton("General");
                context.clickScreenButton("Client");
                context.runOnClient(mc -> check(mc.gui.screen().children().stream()
                                .filter(CycleButton.class::isInstance).map(CycleButton.class::cast)
                                .noneMatch(control -> control.active),
                        "Malformed client.json left client controls enabled"));
                check(!GemsClientConfigManager.save(changedClient)
                                && "{broken".equals(Files.readString(client)),
                        "Malformed client.json was overwritten or allowed to save");
                System.out.println("[config-save] PASS: client unknown key and malformed-file protection");
            } finally {
                restore(balance, originalBalance);
                restore(effective, originalEffective);
                restore(disables, originalDisables);
                restore(client, originalClient);
                GemsClientConfigManager.reloadFromDisk();
                var restored = world.getServer().computeOnServer(server -> GemsBalance.reloadFromDisk());
                check(restored.applied(), "Could not restore the original integrated-server balance");
                var restoredDisables = GemsDisablesConfigManager.loadOrCreateStrict();
                check(restoredDisables.config() != null, "Could not restore original disables");
                world.getServer().runOnServer(server -> GemsDisables.apply(restoredDisables.config()));
                context.setScreen(() -> null);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Config save fixture I/O failed", e);
        }
    }

    private static EditBox firstField(Screen screen) {
        return screen.children().stream().filter(EditBox.class::isInstance)
                .map(EditBox.class::cast).findFirst()
                .orElseThrow(() -> new IllegalStateException("Fire settings field missing"));
    }

    private static Button button(Screen screen, String label) {
        return screen.children().stream().filter(Button.class::isInstance)
                .map(Button.class::cast).filter(value -> label.equals(value.getMessage().getString()))
                .findFirst().orElseThrow(() -> new IllegalStateException(label + " button missing"));
    }

    private static byte[] readIfPresent(Path path) throws IOException {
        return Files.exists(path) ? Files.readAllBytes(path) : null;
    }

    private static void restore(Path path, byte[] bytes) throws IOException {
        if (bytes == null) Files.deleteIfExists(path);
        else Files.write(path, bytes);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
