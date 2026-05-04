package com.feel.gems.gametest.adventure;

import com.feel.gems.adventure.CampaignPersistentState;
import com.feel.gems.adventure.CampaignRewards;
import com.feel.gems.adventure.LandmarkKey;
import com.feel.gems.friend.FriendFeatures;
import com.feel.gems.gametest.util.GemsGameTestUtil;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;

/** Validates the packaged templates with Minecraft's own structure loader. */
public final class AdventureStructureGameTests {
    private static final Set<String> ROOTS = Set.of("framefall_gallery", "lastlight_exchange",
            "rehearsal_works", "branch_vault", "redline_house",
            "unwritten_court", "second_chance_town", "inkbound_workshop",
            "khaled_floodgate", "nour_signal_harbor",
            "fam_forum", "adl_survey_house", "othman_library",
            "yehia_story_lodge", "omar_lost_quarter", "soryal_repair_yard",
            "ali_splitstone_cloister");
    private static final Map<String, String> SATELLITE_ROOTS = Map.ofEntries(
            Map.entry("framefall_atelier", "framefall_gallery"),
            Map.entry("framefall_garden", "framefall_gallery"),
            Map.entry("framefall_observatory", "framefall_gallery"),
            Map.entry("lastlight_market", "lastlight_exchange"),
            Map.entry("lastlight_signal_box", "lastlight_exchange"),
            Map.entry("rehearsal_prop_foundry", "rehearsal_works"),
            Map.entry("rehearsal_costume_house", "rehearsal_works"),
            Map.entry("branch_escape_tower", "branch_vault"),
            Map.entry("branch_map_room", "branch_vault"),
            Map.entry("redline_infirmary", "redline_house"),
            Map.entry("redline_watchpost", "redline_house"),
            Map.entry("unwritten_stewards_annex", "unwritten_court"),
            Map.entry("unwritten_mask_archive", "unwritten_court"),
            Map.entry("town_casino", "second_chance_town"),
            Map.entry("town_arcade", "second_chance_town"),
            Map.entry("town_lounge", "second_chance_town"),
            Map.entry("town_market", "second_chance_town"),
            Map.entry("town_guesthouse", "second_chance_town"),
            Map.entry("town_hall", "second_chance_town"),
            Map.entry("inkbound_print_house", "inkbound_workshop"),
            Map.entry("inkbound_garden", "inkbound_workshop"),
            Map.entry("khaled_shieldworks", "khaled_floodgate"),
            Map.entry("khaled_shelter_walk", "khaled_floodgate"),
            Map.entry("nour_boathouse", "nour_signal_harbor"),
            Map.entry("nour_foghouse", "nour_signal_harbor"),
            Map.entry("fam_story_hall", "fam_forum"),
            Map.entry("fam_open_school", "fam_forum"),
            Map.entry("adl_sightpost", "adl_survey_house"),
            Map.entry("adl_chart_shelter", "adl_survey_house"),
            Map.entry("othman_catalog_rotunda", "othman_library"),
            Map.entry("othman_study_wing", "othman_library"),
            Map.entry("yehia_truth_garden", "yehia_story_lodge"),
            Map.entry("yehia_prop_house", "yehia_story_lodge"),
            Map.entry("omar_exchange_court", "omar_lost_quarter"),
            Map.entry("omar_memory_loft", "omar_lost_quarter"),
            Map.entry("omar_return_oasis", "omar_lost_quarter"),
            Map.entry("soryal_scrap_bridge", "soryal_repair_yard"),
            Map.entry("soryal_repair_loft", "soryal_repair_yard"),
            Map.entry("ali_high_map_room", "ali_splitstone_cloister"),
            Map.entry("ali_cut_passage", "ali_splitstone_cloister")
    );
    private static final Map<String, Vec3i> SIZES = Map.ofEntries(
            Map.entry("framefall_gallery", new Vec3i(47, 31, 47)),
            Map.entry("lastlight_exchange", new Vec3i(47, 31, 47)),
            Map.entry("rehearsal_works", new Vec3i(47, 31, 47)),
            Map.entry("branch_vault", new Vec3i(47, 31, 47)),
            Map.entry("redline_house", new Vec3i(47, 31, 47)),
            Map.entry("unwritten_court", new Vec3i(47, 31, 47)),
            Map.entry("second_chance_town", new Vec3i(63, 31, 63)),
            Map.entry("framefall_atelier", new Vec3i(19, 18, 19)),
            Map.entry("framefall_garden", new Vec3i(19, 15, 19)),
            Map.entry("framefall_observatory", new Vec3i(17, 25, 17)),
            Map.entry("lastlight_market", new Vec3i(21, 22, 21)),
            Map.entry("lastlight_signal_box", new Vec3i(21, 22, 21)),
            Map.entry("rehearsal_prop_foundry", new Vec3i(21, 22, 21)),
            Map.entry("rehearsal_costume_house", new Vec3i(21, 22, 21)),
            Map.entry("branch_escape_tower", new Vec3i(21, 22, 21)),
            Map.entry("branch_map_room", new Vec3i(21, 22, 21)),
            Map.entry("redline_infirmary", new Vec3i(21, 22, 21)),
            Map.entry("redline_watchpost", new Vec3i(21, 22, 21)),
            Map.entry("unwritten_stewards_annex", new Vec3i(21, 22, 21)),
            Map.entry("unwritten_mask_archive", new Vec3i(21, 22, 21)),
            Map.entry("town_casino", new Vec3i(31, 25, 31)),
            Map.entry("town_arcade", new Vec3i(21, 22, 15)),
            Map.entry("town_lounge", new Vec3i(21, 22, 15)),
            Map.entry("town_market", new Vec3i(21, 22, 21)),
            Map.entry("town_guesthouse", new Vec3i(21, 22, 21)),
            Map.entry("town_hall", new Vec3i(31, 25, 31)),
            Map.entry("inkbound_workshop", new Vec3i(47, 31, 47)),
            Map.entry("inkbound_print_house", new Vec3i(21, 22, 21)),
            Map.entry("inkbound_garden", new Vec3i(21, 22, 21)),
            Map.entry("khaled_floodgate", new Vec3i(47, 31, 47)),
            Map.entry("khaled_shieldworks", new Vec3i(21, 22, 21)),
            Map.entry("khaled_shelter_walk", new Vec3i(21, 22, 21)),
            Map.entry("nour_signal_harbor", new Vec3i(47, 31, 47)),
            Map.entry("nour_boathouse", new Vec3i(21, 22, 21)),
            Map.entry("nour_foghouse", new Vec3i(21, 22, 21)),
            Map.entry("fam_forum", new Vec3i(47, 31, 47)),
            Map.entry("fam_story_hall", new Vec3i(21, 22, 21)),
            Map.entry("fam_open_school", new Vec3i(21, 22, 21)),
            Map.entry("adl_survey_house", new Vec3i(47, 31, 47)),
            Map.entry("adl_sightpost", new Vec3i(21, 22, 21)),
            Map.entry("adl_chart_shelter", new Vec3i(21, 22, 21)),
            Map.entry("othman_library", new Vec3i(47, 31, 47)),
            Map.entry("othman_catalog_rotunda", new Vec3i(21, 22, 21)),
            Map.entry("othman_study_wing", new Vec3i(21, 22, 21)),
            Map.entry("yehia_story_lodge", new Vec3i(47, 31, 47)),
            Map.entry("yehia_truth_garden", new Vec3i(21, 22, 21)),
            Map.entry("yehia_prop_house", new Vec3i(21, 22, 21)),
            Map.entry("omar_lost_quarter", new Vec3i(47, 31, 47)),
            Map.entry("omar_exchange_court", new Vec3i(21, 22, 21)),
            Map.entry("omar_memory_loft", new Vec3i(21, 22, 21)),
            Map.entry("omar_return_oasis", new Vec3i(21, 22, 21)),
            Map.entry("soryal_repair_yard", new Vec3i(47, 31, 47)),
            Map.entry("soryal_scrap_bridge", new Vec3i(21, 22, 21)),
            Map.entry("soryal_repair_loft", new Vec3i(21, 22, 21)),
            Map.entry("ali_splitstone_cloister", new Vec3i(47, 31, 47)),
            Map.entry("ali_high_map_room", new Vec3i(21, 22, 21)),
            Map.entry("ali_cut_passage", new Vec3i(21, 22, 21))
    );

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 80)
    public void packagedLandmarksLoadWithTheirLootChests(GameTestHelper context) {
        var manager = context.getLevel().getServer().getStructureTemplateManager();
        for (var entry : SIZES.entrySet()) {
            String name = entry.getKey();
            StructureTemplate template = manager.get(Identifier.fromNamespaceAndPath("gems", name))
                    .orElseThrow(() -> new IllegalStateException("Missing adventure structure " + name));
            if (!template.getSize().equals(entry.getValue())) {
                context.fail("Wrong size for " + name);
                return;
            }
            var chests = template.filterBlocks(BlockPos.ZERO,
                    new StructurePlaceSettings(), Blocks.CHEST, false);
            int expected = ROOTS.contains(name) ? 2 : 1;
            if (chests.size() != expected) {
                context.fail("Wrong chest count for " + name + ": expected "
                        + expected + ", found " + chests.size());
                return;
            }
            for (var chest : chests) {
                String expectedLoot = "gems:chests/"
                        + SATELLITE_ROOTS.getOrDefault(name, name);
                if (chest.nbt() == null
                        || !expectedLoot.equals(chest.nbt().getStringOr("LootTable", ""))) {
                    context.fail("Wrong chest loot for " + name);
                    return;
                }
            }
        }
        for (String name : SIZES.keySet()) {
            if (ROOTS.contains(name)) {
                var tag = TagKey.<Structure>create(Registries.STRUCTURE,
                        Identifier.fromNamespaceAndPath("gems", "atlas_target/" + name));
                var registry = context.getLevel().getServer().registryAccess()
                        .lookupOrThrow(Registries.STRUCTURE);
                int targetCount = 0;
                for (var ignored : registry.getTagOrEmpty(tag)) {
                    targetCount++;
                }
                int expectedTargets = name.equals("soryal_repair_yard")
                        || name.equals("othman_library") ? 2 : 1;
                if (targetCount != expectedTargets) {
                    context.fail("Atlas target is not registered: " + name);
                    return;
                }
            }
        }
        context.succeed();
    }

    @GameTest(structure = "gems:second_chance_town", maxTicks = 100)
    public void placedTownHasSolidRoutesAndLoot(GameTestHelper context) {
        if (!context.getBlockState(new BlockPos(31, 4, 15)).is(Blocks.CALCITE)
                || !context.getBlockState(new BlockPos(12, 5, 48)).is(Blocks.CHEST)
                || !context.getBlockState(new BlockPos(40, 5, 31)).isAir()) {
            context.fail("Town template did not place its path, loot chest or clear resident space");
            return;
        }
        if (!context.getEntities(FriendFeatures.FRIEND).isEmpty()) {
            context.fail("A friend remained in the town root after moving to their own domain");
            return;
        }
        context.succeed();
    }

    @GameTest(structure = "gems:town_casino", maxTicks = 100)
    public void placedCasinoHasRagab(GameTestHelper context) {
        if (context.getEntities(FriendFeatures.FRIEND).size() != 1) {
            context.fail("Ragab did not spawn in the casino");
            return;
        }
        var ragab = context.getEntities(FriendFeatures.FRIEND).getFirst();
        if (!"ragab".equals(ragab.friendId())) {
            context.fail("The casino resident lost Ragab's saved identity");
            return;
        }
        var player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        player.snapTo(ragab.getX() + 1.0, ragab.getY(), ragab.getZ(), 0.0F, 0.0F);
        float health = ragab.getHealth();
        player.attack(ragab);
        if (!ragab.isAlive() || ragab.getHealth() != health
                || ragab.hurtServer(context.getLevel(), context.getLevel().damageSources().magic(), 30.0F)) {
            context.fail("A player or area attack injured the casino resident");
            return;
        }
        if (ragab.interact(player, InteractionHand.MAIN_HAND,
                new Vec3(0.0, ragab.getBbHeight() * 0.5, 0.0))
                != InteractionResult.SUCCESS_SERVER) {
            context.fail("Ragab did not open the casino after the attack");
            return;
        }
        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 80)
    public void victoryGivesEachParticipantOneIdentityBook(GameTestHelper context) {
        var first = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        var second = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        var server = context.getLevel().getServer();
        var firstVictory = CampaignRewards.recordVictory(server, List.of(first, second),
                LandmarkKey.FRAMEFALL);
        if (!firstVictory.firstClears().get(first.getUUID())
                || !firstVictory.firstClears().get(second.getUUID())) {
            context.fail("New participants' first clears were not recorded");
            return;
        }
        if (identityBookCount(first) != 1 || identityBookCount(second) != 1) {
            context.fail("Each participant needs one identity book");
            return;
        }
        var state = CampaignPersistentState.get(server).model();
        if (state.needsLore(first.getUUID(), LandmarkKey.FRAMEFALL)
                || state.needsLore(second.getUUID(), LandmarkKey.FRAMEFALL)) {
            context.fail("Delivered books remain pending");
            return;
        }
        CampaignRewards.recordVictory(server, List.of(first, second), LandmarkKey.FRAMEFALL);
        if (identityBookCount(first) != 1 || identityBookCount(second) != 1) {
            context.fail("Rematch duplicated an identity book");
            return;
        }
        context.succeed();
    }

    private static int identityBookCount(net.minecraft.server.level.ServerPlayer player) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            var stack = player.getInventory().getItem(slot);
            if (!stack.is(Items.WRITTEN_BOOK)) {
                continue;
            }
            var content = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
            if (content != null && content.getPages(false).stream()
                    .anyMatch(page -> page.getString().contains("Mohanad"))) {
                count++;
            }
        }
        return count;
    }
}
