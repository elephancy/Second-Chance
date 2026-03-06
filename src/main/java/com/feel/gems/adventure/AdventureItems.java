package com.feel.gems.adventure;

import com.feel.gems.GemsMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

/** Registers expedition items before the creative tab is built. */
public final class AdventureItems {
    public static final Item RUMOR_ATLAS = register("rumor_atlas",
            new RumorAtlasItem(new Item.Properties().setId(key("rumor_atlas")).stacksTo(1)));
    public static final Item SIGNAL_BUOY = register("signal_buoy",
            new SignalBuoyItem(new Item.Properties().setId(key("signal_buoy")).stacksTo(1)));
    public static final Item ATLAS_PIN = register("atlas_pin",
            new AtlasPinItem(new Item.Properties().setId(key("atlas_pin")).stacksTo(1)));
    public static final Item STORY_THREAD = register("story_thread",
            new StoryThreadItem(new Item.Properties().setId(key("story_thread")).stacksTo(1)));
    public static final Item RIVERWARD_PLUME = register("riverward_plume",
            new RiverwardPlumeItem(new Item.Properties().setId(key("riverward_plume")).stacksTo(16)));
    public static final Item SHORTCUT_CHALK = register("shortcut_chalk",
            new ShortcutChalkItem(new Item.Properties().setId(key("shortcut_chalk")).stacksTo(1)));
    public static final Item FORUM_DISPATCH = register("forum_dispatch",
            new Item(new Item.Properties().setId(key("forum_dispatch")).stacksTo(1)));
    public static final Item RECOVERY_SLIP = register("recovery_slip",
            new RecoverySlipItem(new Item.Properties().setId(key("recovery_slip")).stacksTo(1)));
    public static final Item ANGLE_LENS = register("angle_lens",
            new AngleLensItem(new Item.Properties().setId(key("angle_lens")).stacksTo(1)));
    public static final Item RETURN_STUB = register("return_stub",
            new ReturnStubItem(new Item.Properties().setId(key("return_stub")).stacksTo(1)));
    public static final Item DISPATCH_BELL = register("dispatch_bell",
            new DispatchBellItem(new Item.Properties().setId(key("dispatch_bell")).stacksTo(1)));

    private AdventureItems() {
    }

    public static void init() {
        // Forces static registration during mod initialization.
    }

    private static Item register(String path, Item item) {
        return Registry.register(BuiltInRegistries.ITEM, Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, path), item);
    }

    private static ResourceKey<Item> key(String path) {
        return ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(GemsMod.MOD_ID, path));
    }
}
