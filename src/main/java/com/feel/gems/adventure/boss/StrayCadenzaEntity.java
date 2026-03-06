package com.feel.gems.adventure.boss;

import com.feel.gems.adventure.CampaignRewards;
import com.feel.gems.adventure.LandmarkKey;
import com.feel.gems.adventure.AdventureParticles;
import com.feel.gems.adventure.AdventureSounds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.ChatFormatting;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.Level;

/** Three stage moves answer the party's movement and grouping, each with a lock and escape. */
public final class StrayCadenzaEntity extends Monster {
    private static final int ARENA_RADIUS = 19;
    private static final int LOCK_TICK = 46;
    private static final int STRIKE_TICK = 72;
    private static final int CYCLE_TICKS = 132;
    private static final DustParticleOptions SPOTLIGHT = new DustParticleOptions(0xC58CEB, 1.2F);
    private static final DustParticleOptions CROSSCUT = new DustParticleOptions(0xEE766A, 1.2F);
    private static final DustParticleOptions CHORUS = new DustParticleOptions(0x92D6E9, 1.2F);
    private static final DustParticleOptions LOCKED = new DustParticleOptions(0xF6D171, 1.3F);

    private final Map<UUID, Integer> presenceTicks = new HashMap<>();
    private final Map<UUID, Vec3> lastPositions = new HashMap<>();
    private final Map<UUID, Double> travel = new HashMap<>();
    private final Set<UUID> contributors = new HashSet<>();
    private BlockPos arenaCenter;
    private UUID targetId;
    private CadenzaPattern.Act act = CadenzaPattern.Act.SPOTLIGHT;
    private CadenzaPattern.Act requestedAct;
    private double targetX;
    private double targetZ;
    private double travelX;
    private double travelZ;
    private boolean crosscutAlongX;
    private int cycleTick;
    private int rematchReadyAge;
    private boolean unwrittenArena;
    private boolean active;
    private boolean defeatedOnce;

    public StrayCadenzaEntity(EntityType<? extends StrayCadenzaEntity> type, Level world) {
        super(type, world);
        setPersistenceRequired();
        setCustomName(Component.literal("The Stray Cadenza"));
        setCustomNameVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 240.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.FOLLOW_RANGE, 30.0)
                .add(Attributes.ARMOR, 7.0);
    }

    @Override
    protected void registerGoals() {
        // The authored stage, rather than navigation, drives this encounter.
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel world) || !unwrittenArena) return;
        if (arenaCenter == null) arenaCenter = blockPosition();
        if (world.getDifficulty() == Difficulty.PEACEFUL) {
            stop();
            return;
        }
        if (tickCount % 20 == 0) {
            List<ServerPlayer> nearby = nearbyPlayers(world);
            if (!active) {
                if (!defeatedOnce && nearby.stream().anyMatch(player ->
                        player.distanceToSqr(Vec3.atCenterOf(arenaCenter)) < 12 * 12)) {
                    start(nearby);
                }
            } else if (nearby.isEmpty()) {
                stop();
            } else {
                for (ServerPlayer player : nearby) {
                    presenceTicks.merge(player.getUUID(), 20, Integer::sum);
                }
            }
        }
        if (!active) return;
        if (cycleTick % 10 == 0) sampleMovement(nearbyPlayers(world));
        if (cycleTick <= STRIKE_TICK && cycleTick % 3 == 0) drawTell(world);
        if (cycleTick == LOCK_TICK) {
            lockTarget(world);
            announce(nearbyPlayers(world), "The stage is locked. Leave the marked space.");
            BossAudio.tell(nearbyPlayers(world), AdventureSounds.CADENZA_LOCK, 0.8F, 1.0F);
        }
        if (cycleTick == STRIKE_TICK) {
            strike(world);
            announce(nearbyPlayers(world), "The beat passed. Answer now.");
            BossAudio.tell(nearbyPlayers(world), AdventureSounds.CADENZA_STRIKE, 0.9F, 0.85F);
        }
        cycleTick++;
        if (cycleTick >= CYCLE_TICKS) {
            cycleTick = 0;
            chooseAct(nearbyPlayers(world));
        }
    }

    @Override
    public boolean hurtServer(ServerLevel world, DamageSource source, float amount) {
        if (!active || !unwrittenArena) return false;
        Entity attacker = source.getEntity();
        if (attacker instanceof ServerPlayer player) {
            if (arenaCenter == null || player.distanceToSqr(Vec3.atCenterOf(arenaCenter))
                    > (ARENA_RADIUS + 4) * (ARENA_RADIUS + 4)) return false;
            contributors.add(player.getUUID());
        }
        float multiplier = cycleTick > STRIKE_TICK ? 1.0F : 0.25F;
        return super.hurtServer(world, source, Math.min(amount, 18.0F) * multiplier);
    }

    @Override
    public void die(DamageSource source) {
        if (unwrittenArena && level() instanceof ServerLevel world) {
            Set<UUID> eligibleIds = BossParticipation.eligibleIds(presenceTicks, contributors);
            List<ServerPlayer> eligible = CampaignRewards.onlinePlayers(world.getServer(), eligibleIds);
            if (!eligibleIds.isEmpty()) {
                var victory = CampaignRewards.recordVictoryForIds(world.getServer(), eligibleIds,
                        LandmarkKey.UNWRITTEN);
                CampaignSignatures.deliver(eligible);
                if (CadenzaRestoration.light(world, arenaCenter) > 0) {
                    announce(eligible, "The stage remembers the choice you made.");
                }
                CampaignRewards.deliverRematchSupplies(eligible, LandmarkKey.UNWRITTEN, victory);
            }
            defeatedOnce = true;
            stop();
            rematchReadyAge = tickCount + 100;
            setHealth(getMaxHealth());
            if (arenaCenter != null) {
                snapTo(arenaCenter.getX() + 0.5, arenaCenter.getY(),
                        arenaCenter.getZ() + 0.5, getYRot(), getXRot());
            }
            announce(nearbyPlayers(world), "The stage is yours. Speak to the cadenza for a rematch.");
            return;
        }
        super.die(source);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level().isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer) || !unwrittenArena || !defeatedOnce
                || active || tickCount < rematchReadyAge || distanceToSqr(player) > 36.0
                || player.isSpectator()) return InteractionResult.PASS;
        if (level().getDifficulty() == Difficulty.PEACEFUL) {
            serverPlayer.sendSystemMessage(Component.literal("The stage is quiet in peaceful worlds.")
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.SUCCESS_SERVER;
        }
        start(nearbyPlayers((ServerLevel) level()));
        return InteractionResult.SUCCESS_SERVER;
    }

    public boolean requestAct(ServerPlayer player, CadenzaPattern.Act choice) {
        if (!unwrittenArena || choice == null || player.level() != level()
                || player.distanceToSqr(this) > 24 * 24 || player.isSpectator()
                || (!active && !defeatedOnce)) return false;
        requestedAct = choice;
        player.sendSystemMessage(Component.literal("Next beat: " + choice.label + ". " + choice.hint)
                .withStyle(ChatFormatting.GOLD), true);
        return true;
    }

    private void start(List<ServerPlayer> nearby) {
        if (arenaCenter == null) arenaCenter = blockPosition();
        if (defeatedOnce && level() instanceof ServerLevel serverLevel) {
            requestedAct = StageOpeningState.get(serverLevel.getServer()).consume();
        }
        setHealth(getMaxHealth());
        active = true;
        cycleTick = 0;
        presenceTicks.clear();
        contributors.clear();
        lastPositions.clear();
        travel.clear();
        travelX = travelZ = 0.0;
        for (ServerPlayer player : nearby) lastPositions.put(player.getUUID(), player.position());
        chooseAct(nearby);
    }

    private void stop() {
        active = false;
        cycleTick = 0;
        presenceTicks.clear();
        contributors.clear();
        lastPositions.clear();
        travel.clear();
        travelX = travelZ = 0.0;
        requestedAct = null;
    }

    private void sampleMovement(List<ServerPlayer> players) {
        Set<UUID> present = new HashSet<>();
        for (ServerPlayer player : players) {
            UUID id = player.getUUID();
            present.add(id);
            Vec3 now = player.position();
            Vec3 before = lastPositions.put(id, now);
            if (before != null) {
                double dx = Math.abs(now.x - before.x);
                double dz = Math.abs(now.z - before.z);
                travel.merge(id, Math.hypot(dx, dz), Double::sum);
                travelX += dx;
                travelZ += dz;
            }
        }
        lastPositions.keySet().retainAll(present);
        travel.keySet().retainAll(present);
    }

    private void chooseAct(List<ServerPlayer> players) {
        if (players.isEmpty()) return;
        boolean closePair = false;
        for (int i = 0; i < players.size() && !closePair; i++) {
            for (int j = i + 1; j < players.size(); j++) {
                if (players.get(i).distanceToSqr(players.get(j)) <= 4 * 4) {
                    closePair = true;
                    break;
                }
            }
        }
        double mean = players.stream().mapToDouble(player ->
                travel.getOrDefault(player.getUUID(), 0.0)).average().orElse(0.0);
        crosscutAlongX = travelX >= travelZ;
        act = CadenzaPattern.choose(players.size(), closePair, mean, requestedAct);
        requestedAct = null;
        ServerPlayer target = players.stream().min((a, b) ->
                Double.compare(a.distanceToSqr(this), b.distanceToSqr(this))).orElseThrow();
        targetId = target.getUUID();
        captureTarget(target);
        travel.clear();
        travelX = travelZ = 0.0;
        announce(players, switch (act) {
            case SPOTLIGHT -> "Still feet: the spotlight follows, then locks. Move after the lock.";
            case CROSSCUT -> "Running lines: a red cut will lock across the stage. Step aside.";
            case CHORUS -> "Standing together: the center will pulse. Head for the seats.";
        });
        BossAudio.tell(players, AdventureSounds.CADENZA_CALL, 0.7F, 1.05F);
    }

    private void captureTarget(ServerPlayer player) {
        targetX = clamp(player.getX(), arenaCenter.getX() - 8, arenaCenter.getX() + 9);
        targetZ = clamp(player.getZ(), arenaCenter.getZ() - 8, arenaCenter.getZ() + 9);
    }

    private void lockTarget(ServerLevel world) {
        if (act == CadenzaPattern.Act.CHORUS || targetId == null) return;
        ServerPlayer player = world.getServer().getPlayerList().getPlayer(targetId);
        if (player != null && player.level() == world && player.isAlive()) captureTarget(player);
    }

    private void drawTell(ServerLevel world) {
        if (arenaCenter == null) return;
        if (cycleTick < LOCK_TICK && act != CadenzaPattern.Act.CHORUS && targetId != null) {
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(targetId);
            if (player != null && player.level() == world && player.isAlive()) captureTarget(player);
        }
        DustParticleOptions color = cycleTick >= LOCK_TICK ? LOCKED : switch (act) {
            case SPOTLIGHT -> SPOTLIGHT;
            case CROSSCUT -> CROSSCUT;
            case CHORUS -> CHORUS;
        };
        SimpleParticleType stageMark = cycleTick >= LOCK_TICK
                ? AdventureParticles.CADENZA_LOCK : switch (act) {
                    case SPOTLIGHT -> AdventureParticles.CADENZA_SPOTLIGHT;
                    case CROSSCUT -> AdventureParticles.CADENZA_CROSSCUT;
                    case CHORUS -> AdventureParticles.CADENZA_CHORUS;
                };
        boolean showMark = cycleTick % 6 == 0;
        double y = arenaCenter.getY() + 0.2;
        if (act == CadenzaPattern.Act.CROSSCUT) {
            for (int step = -9; step <= 9; step++) {
                world.sendParticles(color,
                        crosscutAlongX ? arenaCenter.getX() + 0.5 + step : targetX,
                        y,
                        crosscutAlongX ? targetZ : arenaCenter.getZ() + 0.5 + step,
                        1, 0.03, 0.02, 0.03, 0);
                if (showMark && (step == -9 || step == 0 || step == 9)) {
                    world.sendParticles(stageMark,
                            crosscutAlongX ? arenaCenter.getX() + 0.5 + step : targetX,
                            y + 0.14,
                            crosscutAlongX ? targetZ : arenaCenter.getZ() + 0.5 + step,
                            1, 0, 0, 0, 0);
                }
            }
        } else {
            double centerX = act == CadenzaPattern.Act.CHORUS ? arenaCenter.getX() + 0.5 : targetX;
            double centerZ = act == CadenzaPattern.Act.CHORUS ? arenaCenter.getZ() + 0.5 : targetZ;
            double radius = act == CadenzaPattern.Act.CHORUS ? 6.2 : 2.8;
            for (int step = 0; step < 16; step++) {
                double angle = step * Math.PI / 8.0;
                world.sendParticles(color, centerX + Math.cos(angle) * radius, y,
                        centerZ + Math.sin(angle) * radius,
                        1, 0.03, 0.02, 0.03, 0);
                if (showMark && step % 4 == 0) {
                    world.sendParticles(stageMark,
                            centerX + Math.cos(angle) * radius, y + 0.14,
                            centerZ + Math.sin(angle) * radius,
                            1, 0, 0, 0, 0);
                }
            }
        }
    }

    private void strike(ServerLevel world) {
        double centerX = arenaCenter.getX() + 0.5;
        double centerZ = arenaCenter.getZ() + 0.5;
        for (ServerPlayer player : nearbyPlayers(world)) {
            if (Math.abs(player.getY() - arenaCenter.getY()) > 2.5) continue;
            boolean hit = switch (act) {
                case SPOTLIGHT -> squared(player.getX() - targetX, player.getZ() - targetZ)
                        <= 2.8 * 2.8;
                case CROSSCUT -> crosscutAlongX
                        ? Math.abs(player.getZ() - targetZ) <= 1.25
                                && Math.abs(player.getX() - centerX) <= 9.5
                        : Math.abs(player.getX() - targetX) <= 1.25
                                && Math.abs(player.getZ() - centerZ) <= 9.5;
                case CHORUS -> squared(player.getX() - centerX, player.getZ() - centerZ)
                        <= 6.2 * 6.2;
            };
            if (hit) player.hurtServer(world, world.damageSources().mobAttack(this), 10.0F);
        }
    }

    private static double squared(double x, double z) {
        return x * x + z * z;
    }

    private static double clamp(double value, double low, double high) {
        return Math.max(low, Math.min(high, value));
    }

    private List<ServerPlayer> nearbyPlayers(ServerLevel world) {
        if (arenaCenter == null) return List.of();
        return new ArrayList<>(world.getEntitiesOfClass(ServerPlayer.class,
                new AABB(arenaCenter).inflate(ARENA_RADIUS),
                player -> player.isAlive() && !player.isSpectator()));
    }

    private void announce(List<ServerPlayer> players, String message) {
        for (ServerPlayer player : players) {
            player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.LIGHT_PURPLE), true);
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput view) {
        super.readAdditionalSaveData(view);
        unwrittenArena = view.getBooleanOr("UnwrittenArena", false);
        defeatedOnce = view.getBooleanOr("DefeatedOnce", false);
        int x = view.getIntOr("ArenaX", Integer.MIN_VALUE);
        int y = view.getIntOr("ArenaY", Integer.MIN_VALUE);
        int z = view.getIntOr("ArenaZ", Integer.MIN_VALUE);
        arenaCenter = x == Integer.MIN_VALUE || y == Integer.MIN_VALUE || z == Integer.MIN_VALUE
                ? null : new BlockPos(x, y, z);
        stop();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput view) {
        super.addAdditionalSaveData(view);
        view.putBoolean("UnwrittenArena", unwrittenArena);
        view.putBoolean("DefeatedOnce", defeatedOnce);
        if (arenaCenter != null) {
            view.putInt("ArenaX", arenaCenter.getX());
            view.putInt("ArenaY", arenaCenter.getY());
            view.putInt("ArenaZ", arenaCenter.getZ());
        }
    }

    @Override
    public boolean isPersistenceRequired() {
        return true;
    }
}
