package com.feel.gems.adventure;

import com.feel.gems.adventure.boss.BossFeatures;
import com.feel.gems.item.ModItemGroups;
import net.minecraft.world.item.ItemStack;

/** Adds campaign content to the Gems tab only when Second Chance is installed. */
public final class AdventureCreativeItems {
    private static boolean registered;

    private AdventureCreativeItems() { }

    public static void register() {
        if (registered) return;
        registered = true;
        ModItemGroups.registerAddonItem(() -> new ItemStack(AdventureItems.RUMOR_ATLAS));
        ModItemGroups.registerAddonItem(() -> new ItemStack(AdventureItems.SIGNAL_BUOY));
        ModItemGroups.registerAddonItem(() -> new ItemStack(AdventureItems.ATLAS_PIN));
        ModItemGroups.registerAddonItem(() -> new ItemStack(AdventureItems.STORY_THREAD));
        ModItemGroups.registerAddonItem(() -> new ItemStack(AdventureItems.SHORTCUT_CHALK));
        ModItemGroups.registerAddonItem(() -> new ItemStack(AdventureItems.RECOVERY_SLIP));
        ModItemGroups.registerAddonItem(() -> new ItemStack(AdventureItems.ANGLE_LENS));
        ModItemGroups.registerAddonItem(() -> new ItemStack(AdventureItems.RETURN_STUB));
        ModItemGroups.registerAddonItem(() -> new ItemStack(DomainBlocks.HUSHWIRE_SLATE));
        ModItemGroups.registerAddonItem(() -> new ItemStack(DomainBlocks.HUSHWIRE_TRACERY));
        ModItemGroups.registerAddonItem(() -> new ItemStack(DomainBlocks.INKBOUND_SCREEN));
        ModItemGroups.registerAddonItem(() -> new ItemStack(DomainBlocks.INKBOUND_INKSTONE));
        ModItemGroups.registerAddonItem(() -> new ItemStack(DomainBlocks.DECO_FAIENCE));
        ModItemGroups.registerAddonItem(() -> new ItemStack(DomainBlocks.GALLERY_PLANE));
        ModItemGroups.registerAddonItem(() -> new ItemStack(DomainBlocks.GALLERY_PRISM));
        ModItemGroups.registerAddonItem(() -> new ItemStack(DomainBlocks.STATION_ASHLAR));
        ModItemGroups.registerAddonItem(() -> new ItemStack(DomainBlocks.STATION_DIAL));
        ModItemGroups.registerAddonItem(() -> new ItemStack(DomainBlocks.STAGE_TIMBER));
        ModItemGroups.registerAddonItem(() -> new ItemStack(DomainBlocks.STAGE_CURTAIN));
        ModItemGroups.registerAddonItem(() -> new ItemStack(DomainBlocks.FOLIO_STONE));
        ModItemGroups.registerAddonItem(() -> new ItemStack(DomainBlocks.BRANCH_INDEX));
        ModItemGroups.registerAddonItem(() -> new ItemStack(DomainBlocks.REFUGE_BRICK));
        ModItemGroups.registerAddonItem(() -> new ItemStack(DomainBlocks.SHELTER_EMBLEM));
        ModItemGroups.registerAddonItem(() -> new ItemStack(DomainBlocks.COURT_PLASTER));
        ModItemGroups.registerAddonItem(() -> new ItemStack(DomainBlocks.MASK_RELIEF));
        ModItemGroups.registerAddonItem(() -> new ItemStack(DomainBlocks.RIVERWARD_LIMESTONE));
        ModItemGroups.registerAddonItem(() -> new ItemStack(DomainBlocks.RIVERWARD_GAUGE));
        ModItemGroups.registerAddonItem(() -> new ItemStack(DomainBlocks.HARBOR_CHALK));
        ModItemGroups.registerAddonItem(() -> new ItemStack(DomainBlocks.HARBOR_WAVE_TILE));
        ModItemGroups.registerAddonItem(() -> new ItemStack(DomainBlocks.FORUM_PLASTER));
        ModItemGroups.registerAddonItem(() -> new ItemStack(DomainBlocks.FORUM_MOSAIC));
        ModItemGroups.registerAddonItem(() -> new ItemStack(DomainBlocks.SURVEY_STONE));
        ModItemGroups.registerAddonItem(() -> new ItemStack(DomainBlocks.COMPASS_MOSAIC));
        ModItemGroups.registerAddonItem(() -> new ItemStack(DomainBlocks.ARCHIVE_LIMESTONE));
        ModItemGroups.registerAddonItem(() -> new ItemStack(DomainBlocks.ARCHIVE_INLAY));
        ModItemGroups.registerAddonItem(() -> new ItemStack(DomainBlocks.STORYBOOK_TURF));
        ModItemGroups.registerAddonItem(() -> new ItemStack(DomainBlocks.STORYBOOK_PATH));
        ModItemGroups.registerAddonItem(() -> new ItemStack(DomainBlocks.LOST_CLAY));
        ModItemGroups.registerAddonItem(() -> new ItemStack(DomainBlocks.LOST_TILE));
        ModItemGroups.registerAddonItem(() -> new ItemStack(DomainBlocks.PATCHWORK_BRICK));
        ModItemGroups.registerAddonItem(() -> new ItemStack(DomainBlocks.PATCHWORK_MOSAIC));
        ModItemGroups.registerAddonItem(() -> new ItemStack(DomainBlocks.SPLITSTONE));
        ModItemGroups.registerAddonItem(() -> new ItemStack(DomainBlocks.WAYMARK_TILE));
        ModItemGroups.registerAddonItem(() -> new ItemStack(BossFeatures.PARALLAX_FRAME));
        ModItemGroups.registerAddonItem(() -> new ItemStack(BossFeatures.RETURN_TICKET));
        ModItemGroups.registerAddonItem(() -> new ItemStack(BossFeatures.REHEARSAL_MARK));
        ModItemGroups.registerAddonItem(() -> new ItemStack(BossFeatures.ROUTE_LENS));
        ModItemGroups.registerAddonItem(() -> new ItemStack(BossFeatures.RESCUE_LINE));
        ModItemGroups.registerAddonItem(() -> new ItemStack(BossFeatures.SIGNAL_DIAL));
        ModItemGroups.registerAddonItem(() -> new ItemStack(BossFeatures.STAGE_MARK));
    }
}
