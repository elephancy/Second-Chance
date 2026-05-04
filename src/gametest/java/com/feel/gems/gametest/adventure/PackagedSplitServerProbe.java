package com.feel.gems.gametest.adventure;

import com.feel.gems.core.GemId;
import com.feel.gems.item.ModItems;
import java.nio.file.Path;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/** Test-only entrypoint, packaged separately from both production jars. */
public final class PackagedSplitServerProbe implements ModInitializer {
    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            String mode = verifyMode();
            verifyRegistries(mode);
            System.out.println("PACKAGED_SPLIT_SERVER_PASS mode=" + mode
                    + " gems=" + origin("gems")
                    + ("both".equals(mode) ? " second_chance=" + origin("second_chance") : ""));
        });
    }

    static String verifyMode() {
        String mode = System.getProperty("gems.packagedSplit.mode", "");
        require("gems".equals(mode) || "both".equals(mode), "No packaged split mode");
        FabricLoader loader = FabricLoader.getInstance();
        require(loader.isModLoaded("gems"), "Gems is not loaded");
        require(loader.isModLoaded("fabric-api"), "Fabric API is not loaded");
        require(loader.isModLoaded("packaged-split-probe"), "Test probe is not loaded");
        require(loader.isModLoaded("second_chance") == "both".equals(mode),
                "Wrong campaign mod presence for " + mode);
        require(loader.isModLoaded("terrablender") == "both".equals(mode),
                "Wrong TerraBlender mod presence for " + mode);
        origin("gems");
        if ("both".equals(mode)) origin("second_chance");
        return mode;
    }

    private static void verifyRegistries(String mode) {
        for (GemId gem : GemId.values()) {
            Identifier expected = Identifier.fromNamespaceAndPath(
                    "gems", gem.name().toLowerCase(java.util.Locale.ROOT) + "_gem");
            require(expected.equals(BuiltInRegistries.ITEM.getKey(ModItems.gemItem(gem))),
                    "Missing packaged gem item: " + gem);
        }
        Identifier atlas = Identifier.fromNamespaceAndPath("gems", "rumor_atlas");
        require(BuiltInRegistries.ITEM.containsKey(atlas) == "both".equals(mode),
                "Campaign item presence does not match " + mode);
    }

    private static Path origin(String id) {
        FabricLoader loader = FabricLoader.getInstance();
        var container = loader.getModContainer(id).orElseThrow();
        var source = container.getOrigin();
        require(source.getPaths().size() == 1, "Expected one jar origin for " + id);
        Path path = source.getPaths().getFirst().toAbsolutePath().normalize();
        Path mods = loader.getGameDir().resolve("mods").toAbsolutePath().normalize();
        require(path.getParent().equals(mods) && path.toString().endsWith(".jar"),
                id + " was loaded from outside the disposable mods directory: " + path);
        return path;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
