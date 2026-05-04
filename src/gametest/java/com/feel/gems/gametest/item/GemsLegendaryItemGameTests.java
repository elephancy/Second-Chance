package com.feel.gems.gametest.item;

import net.minecraft.world.entity.EntityTypes;
import com.feel.gems.config.GemsBalance;
import com.feel.gems.gametest.util.GemsGameTestUtil;
import com.feel.gems.item.ModItems;
import com.feel.gems.item.legendary.DuelistsRapierItem;
import com.feel.gems.item.legendary.EarthsplitterPickItem;
import com.feel.gems.item.legendary.ExperienceBladeItem;
import com.feel.gems.item.legendary.GladiatorsMarkItem;
import com.feel.gems.item.legendary.HuntersTrophyNecklaceItem;
import com.feel.gems.item.legendary.HypnoStaffItem;
import com.feel.gems.item.legendary.ReversalMirrorItem;
import com.feel.gems.item.legendary.SoulShackleItem;
import com.feel.gems.item.legendary.TrackerCompassItem;
import com.feel.gems.legendary.HypnoControl;
import com.feel.gems.legendary.LegendaryCooldowns;
import com.feel.gems.legendary.LegendaryDuels;
import com.feel.gems.legendary.LegendaryPlayerTracker;
import com.feel.gems.legendary.LegendaryWeapons;
import com.feel.gems.legendary.SupremeSetRuntime;
import com.feel.gems.power.registry.ModPassives;
import com.feel.gems.state.GemPlayerState;
import com.feel.gems.state.GemsPersistentDataHolder;
import java.util.EnumSet;
import java.util.Set;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class GemsLegendaryItemGameTests {
    private static void teleport(ServerPlayer player, ServerLevel world, double x, double y, double z, float yaw, float pitch) {
        player.teleportTo(world, x, y, z, EnumSet.noneOf(Relative.class), yaw, pitch, false);
    }

    private static ServerPlayer setupPlayer(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = GemsGameTestUtil.createMockCreativeServerPlayer(context);
        GemsGameTestUtil.forceSurvival(player);
        Vec3 pos = context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
        teleport(player, world, pos.x, pos.y, pos.z, 0.0F, 0.0F);
        GemPlayerState.initIfNeeded(player);
        return player;
    }

    private static void aimAt(ServerPlayer player, ServerLevel world, Vec3 target) {
        Vec3 pos = player.position();
        double dx = target.x - pos.x;
        double dz = target.z - pos.z;
        double dy = target.y - player.getEyeY();
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
        player.teleportTo(world, pos.x, pos.y, pos.z, EnumSet.noneOf(Relative.class), yaw, pitch, false);
    }

    private static Zombie spawnZombie(ServerLevel world, Vec3 pos) {
        Zombie zombie = EntityTypes.ZOMBIE.create(world, EntitySpawnReason.TRIGGERED);
        if (zombie == null) {
            return null;
        }
        zombie.snapTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        world.addFreshEntity(zombie);
        return zombie;
    }

    private static Vec3 origin(GameTestHelper context) {
        return context.absoluteVec(new Vec3(0.5D, 2.0D, 0.5D));
    }

    private static void forceCritical(ServerPlayer player) {
        player.setOnGround(false);
        player.setSprinting(false);
        player.fallDistance = 2.0F;
        player.setDeltaMovement(0.0D, -0.1D, 0.0D);
        player.needsSync = true;
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void trackerCompassSetsTargetAndLodestone(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        MinecraftServer server = world.getServer();
        if (server == null) {
            context.fail("No server instance");
            return;
        }

        ServerPlayer player = setupPlayer(context);
        ServerPlayer target = setupPlayer(context);
        ItemStack compass = new ItemStack(ModItems.TRACKER_COMPASS);
        player.setItemInHand(InteractionHand.MAIN_HAND, compass);

        context.runAtTickTime(5L, () -> {
            LegendaryPlayerTracker.tick(server);
            TrackerCompassItem.setTarget(player, target.getUUID());

            CustomData data = compass.get(DataComponents.CUSTOM_DATA);
            if (data == null) {
                context.fail("Tracker compass missing NBT data");
                return;
            }
            CompoundTag nbt = data.copyTag();
            if (nbt.getStringOr("legendaryTrackTargetName", "").isEmpty()) {
                context.fail("Tracker compass missing target name");
            }
            if (nbt.getIntArray("legendaryTrackPos").isEmpty()) {
                context.fail("Tracker compass missing target position");
            }
            if (nbt.getIntArray("legendaryTrackRespawnPos").isEmpty()) {
                context.fail("Tracker compass missing respawn position");
            }
            LodestoneTracker lodestone = compass.get(DataComponents.LODESTONE_TRACKER);
            if (lodestone == null || lodestone.target().isEmpty()) {
                context.fail("Tracker compass missing lodestone target");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void recallRelicMarksAndTeleports(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = setupPlayer(context);
        ItemStack relic = new ItemStack(ModItems.RECALL_RELIC);
        player.setItemInHand(InteractionHand.MAIN_HAND, relic);

        Vec3 markPos = origin(context);
        teleport(player, world, markPos.x, markPos.y, markPos.z, 0.0F, 0.0F);

        context.runAtTickTime(5L, () -> {
            relic.use(world, player, InteractionHand.MAIN_HAND);
            CompoundTag data = ((GemsPersistentDataHolder) player).gems$getPersistentData();
            if (data.getStringOr("legendaryRecallDim", "").isEmpty() || data.getIntArray("legendaryRecallPos").isEmpty()) {
                context.fail("Recall relic did not set mark");
                return;
            }
            // Move player and recall.
            teleport(player, world, markPos.x + 10.0D, markPos.y, markPos.z + 10.0D, 0.0F, 0.0F);
            relic.use(world, player, InteractionHand.MAIN_HAND);
            if (player.position().distanceTo(markPos.add(0.5D, 1.0D, 0.5D)) > 1.5D) {
                context.fail("Recall relic did not teleport to mark");
                return;
            }
            if (!data.getStringOr("legendaryRecallDim", "").isEmpty()) {
                context.fail("Recall relic should clear mark after teleport");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 400)
    public void hypnoStaffControlsMob(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = setupPlayer(context);
        ItemStack staff = new ItemStack(ModItems.HYPNO_STAFF);
        player.setItemInHand(InteractionHand.MAIN_HAND, staff);

        Vec3 targetPos = origin(context).add(0.0D, 0.0D, 6.0D);
        Zombie target = spawnZombie(world, targetPos);
        if (target == null) {
            context.fail("Failed to spawn zombie");
            return;
        }
        aimAt(player, world, targetPos.add(0.0D, 1.0D, 0.0D));

        int holdTicks = Math.max(1, GemsBalance.v().legendary().hypnoHoldTicks());
        context.runAtTickTime(5L, () -> {
            HypnoStaffItem item = (HypnoStaffItem) staff.getItem();
            for (int i = 0; i < holdTicks; i++) {
                item.onUseTick(world, player, staff, 72000 - i);
            }
            if (!HypnoControl.isHypno(target)) {
                context.fail("Hypno staff did not control mob");
                return;
            }
            if (!player.getUUID().equals(HypnoControl.ownerUuid(target))) {
                context.fail("Hypno staff owner mismatch");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void earthsplitterPickTogglesModeAndBreaksCube(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = setupPlayer(context);
        ItemStack pick = new ItemStack(ModItems.EARTHSPLITTER_PICK);
        player.setItemInHand(InteractionHand.MAIN_HAND, pick);

        int radius = Math.max(1, GemsBalance.v().legendary().earthsplitterRadiusBlocks());
        BlockPos center = player.blockPosition().offset(0, 1, 0);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    world.setBlockAndUpdate(center.offset(dx, dy, dz), Blocks.STONE.defaultBlockState());
                }
            }
        }
        BlockPos bedrock = center.offset(radius, radius, radius);
        world.setBlockAndUpdate(bedrock, Blocks.BEDROCK.defaultBlockState());

        context.runAtTickTime(5L, () -> {
            EarthsplitterPickItem item = (EarthsplitterPickItem) pick.getItem();
            item.mineBlock(pick, world, Blocks.STONE.defaultBlockState(), center, player);
            if (!world.getBlockState(bedrock).is(Blocks.BEDROCK)) {
                context.fail("Earthsplitter should not break unbreakable blocks");
                return;
            }
            int broken = 0;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (world.getBlockState(center.offset(dx, dy, dz)).isAir()) {
                            broken++;
                        }
                    }
                }
            }
            if (broken < (radius * 2 + 1) * (radius * 2 + 1)) {
                context.fail("Earthsplitter did not break cube blocks");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void earthsplitterPickAppliesSilkTouch(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = setupPlayer(context);
        ItemStack pick = new ItemStack(ModItems.EARTHSPLITTER_PICK);

        context.runAtTickTime(5L, () -> {
            EarthsplitterPickItem item = (EarthsplitterPickItem) pick.getItem();
            item.inventoryTick(pick, world, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
            int level = EnchantmentHelper.getItemEnchantmentLevel(world.registryAccess().get(Enchantments.SILK_TOUCH).orElseThrow(), pick);
            if (level < 1) {
                context.fail("Earthsplitter should apply Silk Touch");
            }
            pick.use(world, player, InteractionHand.MAIN_HAND);
            CustomData data = pick.get(DataComponents.CUSTOM_DATA);
            if (data == null || !data.copyTag().getBooleanOr("legendaryEarthsplitterTunnel", false)) {
                context.fail("Earthsplitter should toggle tunnel mode");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void supremeSetAppliesEffects(GameTestHelper context) {
        ServerPlayer player = setupPlayer(context);
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(ModItems.SUPREME_HELMET));
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(ModItems.SUPREME_CHESTPLATE));
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, new ItemStack(ModItems.SUPREME_LEGGINGS));
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, new ItemStack(ModItems.SUPREME_BOOTS));

        context.runAtTickTime(5L, () -> {
            SupremeSetRuntime.tick(player);
            if (!player.hasEffect(MobEffects.NIGHT_VISION)
                    || !player.hasEffect(MobEffects.WATER_BREATHING)
                    || !player.hasEffect(MobEffects.STRENGTH)
                    || !player.hasEffect(MobEffects.FIRE_RESISTANCE)
                    || !player.hasEffect(MobEffects.SPEED)
                    || !player.hasEffect(MobEffects.RESISTANCE)) {
                context.fail("Supreme set did not apply all effects");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void bloodOathBladeTracksUniqueKills(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer killer = setupPlayer(context);
        ServerPlayer victimA = setupPlayer(context);
        ServerPlayer victimB = setupPlayer(context);
        ServerPlayer victimC = setupPlayer(context);
        ItemStack blade = new ItemStack(ModItems.BLOOD_OATH_BLADE);
        killer.setItemInHand(InteractionHand.MAIN_HAND, blade);

        context.runAtTickTime(5L, () -> {
            LegendaryWeapons.onPlayerKill(killer, victimA);
            LegendaryWeapons.onPlayerKill(killer, victimA);
            LegendaryWeapons.onPlayerKill(killer, victimB);
            int sharpness = EnchantmentHelper.getItemEnchantmentLevel(killer.level().registryAccess().get(Enchantments.SHARPNESS).orElseThrow(), blade);
            if (sharpness != 2) {
                context.fail("Blood Oath blade should grant Sharpness per unique kill");
            }
            CustomData data = blade.get(DataComponents.CUSTOM_DATA);
            if (data == null || data.copyTag().getListOrEmpty("legendaryBloodOathKills").size() != 2) {
                context.fail("Blood Oath blade should store unique kills");
                return;
            }
            int cap = GemsBalance.v().legendary().bloodOathSharpnessCap();
            EnchantmentHelper.updateEnchantments(blade, builder -> builder.set(world.registryAccess().get(Enchantments.SHARPNESS).orElseThrow(), cap));
            LegendaryWeapons.onPlayerKill(killer, victimC);
            int capped = EnchantmentHelper.getItemEnchantmentLevel(world.registryAccess().get(Enchantments.SHARPNESS).orElseThrow(), blade);
            if (capped > cap) {
                context.fail("Blood Oath blade should respect sharpness cap");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void demolitionBladeSpawnsTnt(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = setupPlayer(context);
        ItemStack blade = new ItemStack(ModItems.DEMOLITION_BLADE);
        player.setItemInHand(InteractionHand.MAIN_HAND, blade);

        Vec3 targetPos = origin(context).add(0.0D, 0.0D, 6.0D);
        Zombie target = spawnZombie(world, targetPos);
        if (target == null) {
            context.fail("Failed to spawn zombie");
            return;
        }
        aimAt(player, world, targetPos.add(0.0D, 1.0D, 0.0D));
        AABB box = new AABB(target.blockPosition()).inflate(4.0);
        int before = world.getEntitiesOfClass(PrimedTnt.class, box, e -> true).size();

        context.runAtTickTime(5L, () -> {
            blade.use(world, player, InteractionHand.MAIN_HAND);
            int after = world.getEntitiesOfClass(PrimedTnt.class, box, e -> true).size();
            int count = GemsBalance.v().legendary().demolitionTntCount();
            if (after - before < count) {
                context.fail("Demolition blade did not spawn expected TNT count");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void gemSeerOpensScreen(GameTestHelper context) {
        ServerPlayer player = setupPlayer(context);
        ItemStack seer = new ItemStack(ModItems.GEM_SEER);
        player.setItemInHand(InteractionHand.MAIN_HAND, seer);

        context.runAtTickTime(5L, () -> {
            seer.use(player.level(), player, InteractionHand.MAIN_HAND);
            if (!(player.containerMenu instanceof com.feel.gems.screen.GemSeerScreenHandler)) {
                context.fail("Gem Seer should open its screen");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void duelistsRapierParryWindowAndCrit(GameTestHelper context) {
        ServerPlayer player = setupPlayer(context);
        ItemStack rapier = new ItemStack(ModItems.DUELISTS_RAPIER);
        player.setItemInHand(InteractionHand.MAIN_HAND, rapier);

        context.runAtTickTime(5L, () -> {
            rapier.use(player.level(), player, InteractionHand.MAIN_HAND);
            if (!DuelistsRapierItem.isInParryWindow(player)) {
                context.fail("Duelist's Rapier should start parry window");
                return;
            }
            DuelistsRapierItem.onSuccessfulParry(player);
            if (!DuelistsRapierItem.hasAndConsumeGuaranteedCrit(player)) {
                context.fail("Duelist's Rapier should grant guaranteed crit");
                return;
            }
            if (DuelistsRapierItem.hasAndConsumeGuaranteedCrit(player)) {
                context.fail("Duelist's Rapier crit should be single-use");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void experienceBladeConsumesXpAndPersists(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = setupPlayer(context);
        ItemStack blade = new ItemStack(ModItems.EXPERIENCE_BLADE);
        player.setItemInHand(InteractionHand.MAIN_HAND, blade);
        player.giveExperienceLevels(30);

        int perTier = GemsBalance.v().legendary().experienceBladeSharpnessPerTier();
        int xpPerTier = GemsBalance.v().legendary().experienceBladeXpLevelsPerTier();
        int expected = Math.min(GemsBalance.v().legendary().experienceBladeMaxSharpness(), perTier);

        context.runAtTickTime(5L, () -> {
            blade.use(world, player, InteractionHand.MAIN_HAND);
            if (ExperienceBladeItem.getCurrentSharpness(player) != expected) {
                context.fail("Experience blade did not store sharpness");
                return;
            }
            int sharpness = EnchantmentHelper.getItemEnchantmentLevel(world.registryAccess().get(Enchantments.SHARPNESS).orElseThrow(), blade);
            if (sharpness < expected) {
                context.fail("Experience blade did not apply sharpness");
                return;
            }
            if (player.experienceLevel != 30 - xpPerTier) {
                context.fail("Experience blade did not consume xp");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void experienceBladeClearsOnDeath(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = setupPlayer(context);
        ItemStack blade = new ItemStack(ModItems.EXPERIENCE_BLADE);
        player.getInventory().setItem(0, blade);
        ExperienceBladeItem.setCurrentSharpness(player, 6);
        EnchantmentHelper.updateEnchantments(blade, builder -> builder.set(world.registryAccess().get(Enchantments.SHARPNESS).orElseThrow(), 6));

        context.runAtTickTime(5L, () -> {
            ExperienceBladeItem.clearOnDeath(player);
            if (ExperienceBladeItem.getCurrentSharpness(player) != 0) {
                context.fail("Experience blade should clear stored sharpness on death");
                return;
            }
            int level = EnchantmentHelper.getItemEnchantmentLevel(world.registryAccess().get(Enchantments.SHARPNESS).orElseThrow(), blade);
            if (level != 0) {
                context.fail("Experience blade should remove Sharpness on death");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void reversalMirrorReflectsDamage(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer defender = setupPlayer(context);
        ServerPlayer attacker = setupPlayer(context);
        ItemStack mirror = new ItemStack(ModItems.REVERSAL_MIRROR);
        defender.setItemInHand(InteractionHand.MAIN_HAND, mirror);

        context.runAtTickTime(5L, () -> {
            mirror.use(world, defender, InteractionHand.MAIN_HAND);
            if (!ReversalMirrorItem.isActive(defender)) {
                context.fail("Reversal Mirror should be active");
                return;
            }
            float before = attacker.getHealth();
            boolean reflected = ReversalMirrorItem.tryReflectDamage(defender, attacker, 4.0F, world);
            if (!reflected) {
                context.fail("Reversal Mirror did not reflect damage");
                return;
            }
            if (attacker.getHealth() >= before) {
                context.fail("Reversal Mirror did not damage attacker");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void trophyNecklaceStoresPassives(GameTestHelper context) {
        ServerPlayer player = setupPlayer(context);
        ItemStack necklace = new ItemStack(ModItems.HUNTERS_TROPHY_NECKLACE);
        player.setItemInHand(InteractionHand.MAIN_HAND, necklace);

        var passive = ModPassives.all().values().stream().findFirst().orElse(null);
        if (passive == null) {
            context.fail("No passives registered");
            return;
        }

        context.runAtTickTime(5L, () -> {
            boolean stolen = HuntersTrophyNecklaceItem.stealPassive(player, passive.id());
            if (!stolen) {
                context.fail("Trophy necklace did not steal passive");
                return;
            }
            Set<net.minecraft.resources.Identifier> stolenIds = HuntersTrophyNecklaceItem.getStolenPassives(player);
            if (!stolenIds.contains(passive.id())) {
                context.fail("Trophy necklace missing stored passive");
                return;
            }
            if (!HuntersTrophyNecklaceItem.unstealPassive(player, passive.id())) {
                context.fail("Trophy necklace did not unsteal passive");
                return;
            }
            if (HuntersTrophyNecklaceItem.getStolenPassives(player).contains(passive.id())) {
                context.fail("Trophy necklace should remove passive on unsteal");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void gladiatorsMarkLinksPlayers(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = setupPlayer(context);
        ServerPlayer target = setupPlayer(context);
        ItemStack mark = new ItemStack(ModItems.GLADIATORS_MARK);
        player.setItemInHand(InteractionHand.MAIN_HAND, mark);

        Vec3 targetPos = origin(context).add(0.0D, 0.0D, 6.0D);
        teleport(target, world, targetPos.x, targetPos.y, targetPos.z, 180.0F, 0.0F);
        aimAt(player, world, targetPos.add(0.0D, 1.0D, 0.0D));

        context.runAtTickTime(5L, () -> {
            mark.use(world, player, InteractionHand.MAIN_HAND);
            if (!GladiatorsMarkItem.isMarkedAgainst(player, target)) {
                context.fail("Gladiator's Mark should link players");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void soulShackleSplitsDamage(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = setupPlayer(context);
        ServerPlayer target = setupPlayer(context);
        ItemStack shackle = new ItemStack(ModItems.SOUL_SHACKLE);
        player.setItemInHand(InteractionHand.MAIN_HAND, shackle);

        Vec3 targetPos = origin(context).add(0.0D, 0.0D, 6.0D);
        teleport(target, world, targetPos.x, targetPos.y, targetPos.z, 180.0F, 0.0F);
        aimAt(player, world, targetPos.add(0.0D, 1.0D, 0.0D));

        context.runAtTickTime(5L, () -> {
            shackle.use(world, player, InteractionHand.MAIN_HAND);
            if (SoulShackleItem.getShackledTarget(player) != target) {
                context.fail("Soul Shackle should link target");
                return;
            }
            float split = SoulShackleItem.getDamageToTransfer(player, 10.0F);
            float expected = 10.0F * GemsBalance.v().legendary().soulShackleSplitRatio();
            if (Math.abs(split - expected) > 0.01F) {
                context.fail("Soul Shackle split ratio mismatch");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void gladiatorsMarkAmplifiesDamage(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer attacker = setupPlayer(context);
        ServerPlayer target = setupPlayer(context);
        ServerPlayer baseline = setupPlayer(context);
        ItemStack mark = new ItemStack(ModItems.GLADIATORS_MARK);
        attacker.setItemInHand(InteractionHand.MAIN_HAND, mark);
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        attacker.setItemInHand(InteractionHand.MAIN_HAND, sword);

        Vec3 targetPos = origin(context).add(0.0D, 0.0D, 4.0D);
        Vec3 baselinePos = origin(context).add(2.0D, 0.0D, 4.0D);
        teleport(target, world, targetPos.x, targetPos.y, targetPos.z, 180.0F, 0.0F);
        teleport(baseline, world, baselinePos.x, baselinePos.y, baselinePos.z, 180.0F, 0.0F);

        context.runAtTickTime(5L, () -> {
            float baseBefore = baseline.getHealth();
            attacker.attack(baseline);
            float baseDamage = baseBefore - baseline.getHealth();

            attacker.setItemInHand(InteractionHand.MAIN_HAND, mark);
            aimAt(attacker, world, targetPos.add(0.0D, 1.0D, 0.0D));
            mark.use(world, attacker, InteractionHand.MAIN_HAND);

            attacker.setItemInHand(InteractionHand.MAIN_HAND, sword);
            float markedBefore = target.getHealth();
            attacker.attack(target);
            float markedDamage = markedBefore - target.getHealth();

            float mult = GladiatorsMarkItem.getDamageMultiplier();
            if (markedDamage < baseDamage * (mult - 0.1F)) {
                context.fail("Gladiator's Mark should amplify damage");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void soulShackleTransfersDamageToTarget(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = setupPlayer(context);
        ServerPlayer target = setupPlayer(context);
        ItemStack shackle = new ItemStack(ModItems.SOUL_SHACKLE);
        player.setItemInHand(InteractionHand.MAIN_HAND, shackle);

        Vec3 targetPos = origin(context).add(0.0D, 0.0D, 6.0D);
        teleport(target, world, targetPos.x, targetPos.y, targetPos.z, 180.0F, 0.0F);
        aimAt(player, world, targetPos.add(0.0D, 1.0D, 0.0D));

        context.runAtTickTime(5L, () -> {
            shackle.use(world, player, InteractionHand.MAIN_HAND);
            float playerBefore = player.getHealth();
            float targetBefore = target.getHealth();
            float damage = 10.0F;
            player.hurtServer(world, world.damageSources().generic(), damage);

            float split = damage * GemsBalance.v().legendary().soulShackleSplitRatio();
            float expectedPlayer = playerBefore - (damage - split);
            float expectedTarget = targetBefore - split;

            if (Math.abs(player.getHealth() - expectedPlayer) > 0.6F) {
                context.fail("Soul Shackle should reduce holder damage");
                return;
            }
            if (Math.abs(target.getHealth() - expectedTarget) > 0.6F) {
                context.fail("Soul Shackle should transfer damage to target");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void chronoCharmStacksCooldowns(GameTestHelper context) {
        ServerPlayer player = setupPlayer(context);
        player.getInventory().setItem(0, new ItemStack(ModItems.CHRONO_CHARM));
        player.getInventory().setItem(1, new ItemStack(ModItems.CHRONO_CHARM));

        context.runAtTickTime(5L, () -> {
            float mult = LegendaryCooldowns.getCooldownMultiplier(player);
            float expected = (float) Math.pow(GemsBalance.v().legendary().chronoCharmCooldownMultiplier(), 2);
            if (Math.abs(mult - expected) > 0.001F) {
                context.fail("Chrono Charm cooldown multiplier mismatch");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void challengersGauntletAwardsEnergyOnNonPlayerDeath(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer challenger = setupPlayer(context);
        ServerPlayer target = setupPlayer(context);
        Vec3 challengerPos = challenger.position();
        Vec3 targetPos = target.position();

        GemPlayerState.setEnergy(challenger, 3);
        GemPlayerState.setEnergy(target, 3);

        context.runAtTickTime(5L, () -> {
            boolean ok = LegendaryDuels.startGauntletDuel(challenger, target);
            if (!ok) {
                context.fail("Gauntlet duel did not start");
                return;
            }
            LegendaryDuels.onDuelParticipantDeathTail(target, world.damageSources().fellOutOfWorld());
            if (GemPlayerState.getEnergy(challenger) != 4) {
                context.fail("Gauntlet winner should gain energy on non-player kill");
                return;
            }
            if (challenger.position().distanceTo(challengerPos) > 1.0D) {
                context.fail("Gauntlet winner should return to original position");
            }
            if (target.position().distanceTo(targetPos) > 10.0D) {
                // Victim returns on respawn, so accept that they may still be in arena.
                context.succeed();
                return;
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void thirdStrikeBladeAddsBonusDamageOnThirdCrit(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = setupPlayer(context);
        ItemStack blade = new ItemStack(ModItems.THIRD_STRIKE_BLADE);
        player.setItemInHand(InteractionHand.MAIN_HAND, blade);

        Zombie target = spawnZombie(world, origin(context).add(0.0D, 0.0D, 2.0D));
        if (target == null) {
            context.fail("Failed to spawn zombie");
            return;
        }
        var health = target.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(40.0D);
        }
        target.setHealth(40.0F);

        context.runAtTickTime(5L, () -> {
            float before1 = target.getHealth();
            forceCritical(player);
            player.attack(target);
            float after1 = target.getHealth();

            forceCritical(player);
            player.attack(target);
            float after2 = target.getHealth();

            forceCritical(player);
            player.attack(target);
            float after3 = target.getHealth();

            float dmg1 = before1 - after1;
            float dmg2 = after1 - after2;
            float dmg3 = after2 - after3;
            float bonus = GemsBalance.v().legendary().thirdStrikeBonusDamage();

            if (dmg3 < dmg1 + Math.max(0.0F, bonus - 0.5F)) {
                context.fail("Third Strike bonus damage not applied");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void huntersSightBowAimsAtLastHit(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = setupPlayer(context);
        ItemStack bow = new ItemStack(ModItems.HUNTERS_SIGHT_BOW);
        player.setItemInHand(InteractionHand.MAIN_HAND, bow);
        player.getInventory().add(new ItemStack(Items.ARROW, 16));

        Vec3 targetPos = origin(context).add(4.0D, 0.0D, 12.0D);
        Zombie target = spawnZombie(world, targetPos);
        if (target == null) {
            context.fail("Failed to spawn zombie");
            return;
        }

        context.runAtTickTime(5L, () -> {
            com.feel.gems.item.legendary.HunterSightBowItem.recordHit(player, target);
            int remaining = bow.getItem().getUseDuration(bow, player) - 20;
            bow.getItem().releaseUsing(bow, world, player, remaining);

            AABB box = new AABB(player.blockPosition()).inflate(6.0D);
            var arrows = world.getEntitiesOfClass(AbstractArrow.class, box, e -> true);
            if (arrows.isEmpty()) {
                context.fail("Hunter's Sight Bow did not fire projectile");
                return;
            }
            AbstractArrow arrow = arrows.get(0);
            Vec3 arrowDir = arrow.getDeltaMovement().normalize();
            Vec3 toTarget = target.getEyePosition().subtract(player.getEyePosition()).normalize();
            if (arrowDir.dot(toTarget) < 0.9D) {
                context.fail("Hunter's Sight Bow did not aim toward last hit target");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 200)
    public void vampiricEdgeHealsOnCritical(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer player = setupPlayer(context);
        ItemStack blade = new ItemStack(ModItems.VAMPIRIC_EDGE);
        player.setItemInHand(InteractionHand.MAIN_HAND, blade);

        Zombie target = spawnZombie(world, origin(context).add(0.0D, 0.0D, 2.0D));
        if (target == null) {
            context.fail("Failed to spawn zombie");
            return;
        }
        var health = target.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(40.0D);
        }
        target.setHealth(40.0F);
        player.setHealth(10.0F);

        context.runAtTickTime(5L, () -> {
            float before = player.getHealth();
            forceCritical(player);
            player.attack(target);
            if (player.getHealth() <= before) {
                context.fail("Vampiric Edge did not heal on crit");
            }
            context.succeed();
        });
    }
}
