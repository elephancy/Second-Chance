package com.feel.gems.adventure.boss;

import com.feel.gems.adventure.CampaignVisits;
import com.feel.gems.adventure.LandmarkKey;

import com.feel.gems.adventure.AdventureSounds;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.Level;

/** Six authored approach sentries share timing but teach different boss rules. */
public final class RegionalSentryEntity extends Monster {
    public enum Region {
        LASTLIGHT("Signal Warden", 0xD7B876, LandmarkKey.LASTLIGHT),
        REHEARSAL("Understudy", 0xE790C8, LandmarkKey.REHEARSAL),
        BRANCH("Margin Shade", 0x7EC8D8, LandmarkKey.BRANCH),
        REDLINE("Watchkeeper", 0xD96463, LandmarkKey.REDLINE),
        HUSHWIRE("Relay Phantom", 0x88D8E6, LandmarkKey.HUSHWIRE),
        UNWRITTEN("Uncast", 0xA794D2, LandmarkKey.UNWRITTEN);

        final String label;
        final DustParticleOptions tell;
        final LandmarkKey landmark;

        Region(String label, int color, LandmarkKey landmark) {
            this.label = label;
            this.tell = new DustParticleOptions(color, 0.9F);
            this.landmark = landmark;
        }
    }

    private static final int RANGE = 10;
    private static final int LOCK_TICK = 26;
    private static final int STRIKE_TICK = 46;
    private static final int RESET_TICK = 82;
    private static final DustParticleOptions LOCK = new DustParticleOptions(0xF0AF6F, 1.1F);
    private static final DustParticleOptions FALSE = new DustParticleOptions(0xD7A86B, 0.9F);
    private static final DustParticleOptions TRUE = new DustParticleOptions(0x88D8E6, 1.1F);

    private final Region region;
    private final Map<UUID, Vec3> lastPositions = new HashMap<>();
    private BlockPos home;
    private BlockPos shelter;
    private UUID targetId;
    private CadenzaPattern.Act unwrittenAct = CadenzaPattern.Act.SPOTLIGHT;
    private double targetX;
    private double targetZ;
    private double travel;
    private boolean alongX;
    private boolean rescued;
    private boolean hintSent;
    private boolean shelterScanned;
    private int routeX;
    private int routeZ;
    private int receiver;
    private int cycleTick;
    private int cueCycle;

    public RegionalSentryEntity(EntityType<? extends RegionalSentryEntity> type,
                                Level world, Region region) {
        super(type, world);
        this.region = region;
        setPersistenceRequired();
        setCustomName(Component.literal(region.label));
        setCustomNameVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.FOLLOW_RANGE, RANGE);
    }

    @Override
    protected void registerGoals() {
        // Each sentry holds an authored teaching position.
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel world)
                || world.getDifficulty() == Difficulty.PEACEFUL) return;
        if (home == null) home = blockPosition();
        if (region == Region.REDLINE && !shelterScanned) {
            shelter = findShelter(world);
            shelterScanned = true;
        }
        if (cycleTick == 0 && tickCount % 10 != 0) return;
        boolean rescueCheck = region == Region.REDLINE && cycleTick < LOCK_TICK
                && !rescued && cycleTick % 2 == 0;
        boolean movementCheck = region == Region.UNWRITTEN && cycleTick % 10 == 0;
        boolean needPlayers = cycleTick == 0 || tickCount % 20 == 0
                || cycleTick == STRIKE_TICK || rescueCheck || movementCheck;
        List<ServerPlayer> players = needPlayers ? nearby(world) : List.of();
        if (needPlayers && players.isEmpty()) {
            cycleTick = 0;
            targetId = null;
            lastPositions.clear();
            travel = 0;
            hintSent = false;
            return;
        }
        if (cycleTick == 0) {
            CampaignVisits.markNearby(world, players, region.landmark);
            choose(world, players);
        }
        if (movementCheck) sample(players);
        if (rescueCheck) checkShelter(world, players);
        if (cycleTick < STRIKE_TICK && cycleTick % 4 == 0) draw(world);
        if (cycleTick == LOCK_TICK) {
            trackTarget(world);
            BossAudio.tell(nearby(world), lockSound(), 0.75F, cuePitch());
        }
        if (cycleTick == STRIKE_TICK) {
            strike(world, players);
            BossAudio.tell(players, strikeSound(), 0.7F, cuePitch());
        }
        cycleTick = cycleTick >= RESET_TICK ? 0 : cycleTick + 1;
    }

    private void choose(ServerLevel world, List<ServerPlayer> players) {
        cueCycle++;
        ServerPlayer target = players.stream()
                .min(Comparator.comparingDouble(player -> player.distanceToSqr(this)))
                .orElseThrow();
        targetId = target.getUUID();
        capture(target);
        rescued = false;
        if (region == Region.LASTLIGHT) alongX = !alongX;
        if (region == Region.HUSHWIRE) receiver = random.nextInt(3);
        if (region == Region.UNWRITTEN) {
            boolean closePair = false;
            for (int i = 0; i < players.size() && !closePair; i++) {
                for (int j = i + 1; j < players.size(); j++) {
                    if (players.get(i).distanceToSqr(players.get(j)) <= 16) {
                        closePair = true;
                        break;
                    }
                }
            }
            unwrittenAct = CadenzaPattern.choose(players.size(), closePair,
                    travel / players.size(), null);
            travel = 0;
        }
        if (!hintSent) {
            target.sendSystemMessage(Component.literal(hint()).withStyle(ChatFormatting.GRAY), true);
            hintSent = true;
        }
        BossAudio.tell(players, callSound(), 0.6F, cuePitch());
    }

    private float cuePitch() {
        return switch (cueCycle % 3) {
            case 0 -> 1.18F;
            case 1 -> 1.25F;
            default -> 1.32F;
        };
    }

    private void sample(List<ServerPlayer> players) {
        Map<UUID, Vec3> current = new HashMap<>();
        for (ServerPlayer player : players) {
            Vec3 now = player.position();
            Vec3 before = lastPositions.get(player.getUUID());
            if (before != null) travel += Math.hypot(now.x - before.x, now.z - before.z);
            current.put(player.getUUID(), now);
        }
        lastPositions.clear();
        lastPositions.putAll(current);
    }

    private void capture(ServerPlayer target) {
        targetX = Math.max(home.getX() - 7, Math.min(home.getX() + 8, target.getX()));
        targetZ = Math.max(home.getZ() - 7, Math.min(home.getZ() + 8, target.getZ()));
        if (region == Region.BRANCH) {
            double dx = targetX - home.getX() - 0.5;
            double dz = targetZ - home.getZ() - 0.5;
            routeX = Math.abs(dx) >= Math.abs(dz) ? (dx >= 0 ? 1 : -1) : 0;
            routeZ = routeX == 0 ? (dz >= 0 ? 1 : -1) : 0;
        }
    }

    private void trackTarget(ServerLevel world) {
        if (targetId == null) return;
        ServerPlayer target = world.getServer().getPlayerList().getPlayer(targetId);
        if (target != null && target.level() == world && target.isAlive()
                && target.distanceToSqr(this) <= RANGE * RANGE) capture(target);
    }

    private void draw(ServerLevel world) {
        if (cycleTick < LOCK_TICK) trackTarget(world);
        DustParticleOptions dust = cycleTick >= LOCK_TICK ? LOCK : region.tell;
        double cx = home.getX() + 0.5;
        double cz = home.getZ() + 0.5;
        double y = home.getY() + 0.2;
        switch (region) {
            case LASTLIGHT -> drawAxis(world, dust,
                    alongX ? cx : targetX, alongX ? targetZ : cz, alongX, 7, y);
            case REHEARSAL -> drawRing(world, dust, targetX, targetZ, 2.2, y);
            case BRANCH -> drawRay(world, dust, cx, cz, routeX, routeZ, 8, y);
            case REDLINE -> {
                if (!rescued && shelter != null) {
                    double dx = shelter.getX() + 0.5 - cx;
                    double dz = shelter.getZ() + 0.5 - cz;
                    double length = Math.hypot(dx, dz);
                    for (int step = 1; step <= 8; step++) {
                        double fraction = step / 8.0;
                        world.sendParticles(dust, cx + dx * fraction, y,
                                cz + dz * fraction, 1, 0.04, 0.02, 0.04, 0);
                    }
                    if (length > 0) world.sendParticles(TRUE, shelter.getX() + 0.5,
                            y + 0.7, shelter.getZ() + 0.5, 3, 0.2, 0.2, 0.2, 0);
                }
            }
            case HUSHWIRE -> {
                for (int i = 0; i < 3; i++) {
                    int[] offset = receiver(i);
                    world.sendParticles(cycleTick >= LOCK_TICK && i == receiver ? TRUE : FALSE,
                            cx + offset[0], y + 0.4, cz + offset[1],
                            3, 0.2, 0.15, 0.2, 0);
                }
                if (cycleTick >= LOCK_TICK) {
                    int[] offset = receiver(receiver);
                    drawRay(world, TRUE, cx, cz, Integer.signum(offset[0]),
                            Integer.signum(offset[1]), 4, y);
                }
            }
            case UNWRITTEN -> {
                switch (unwrittenAct) {
                    case SPOTLIGHT -> drawRing(world, dust, targetX, targetZ, 2.2, y);
                    case CROSSCUT -> drawAxis(world, dust, cx, targetZ, true, 7, y);
                    case CHORUS -> drawRing(world, dust, cx, cz, 5.0, y);
                }
            }
        }
    }

    private void drawAxis(ServerLevel world, DustParticleOptions dust, double cx,
                          double cz, boolean along, int halfLength, double y) {
        for (int step = -halfLength; step <= halfLength; step++) {
            world.sendParticles(dust, cx + (along ? step : 0), y,
                    cz + (along ? 0 : step), 1, 0.03, 0.02, 0.03, 0);
        }
    }

    private void drawRing(ServerLevel world, DustParticleOptions dust,
                          double cx, double cz, double radius, double y) {
        for (int step = 0; step < 12; step++) {
            double angle = Math.PI * step / 6;
            world.sendParticles(dust, cx + Math.cos(angle) * radius, y,
                    cz + Math.sin(angle) * radius, 1, 0.03, 0.02, 0.03, 0);
        }
    }

    private void drawRay(ServerLevel world, DustParticleOptions dust,
                         double cx, double cz, int dx, int dz, int count, double y) {
        for (int step = 1; step <= count; step++) {
            world.sendParticles(dust, cx + dx * step, y, cz + dz * step,
                    1, 0.03, 0.02, 0.03, 0);
        }
    }

    private void checkShelter(ServerLevel world, List<ServerPlayer> players) {
        if (shelter == null) return;
        for (ServerPlayer player : players) {
            if (Math.abs(player.getY() - shelter.getY()) > 2.0) continue;
            if (!RegionalSentryPlan.circle(player.getX(), player.getZ(),
                    shelter.getX() + 0.5, shelter.getZ() + 0.5, 1.2)) continue;
            rescued = true;
            player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 60, 0));
            player.sendSystemMessage(Component.literal("Shelter reached. The watchkeeper's line breaks.")
                    .withStyle(ChatFormatting.GREEN), true);
            BossAudio.tell(List.of(player), AdventureSounds.REDLINE_SHELTER, 0.6F, 1.25F);
            return;
        }
    }

    private void strike(ServerLevel world, List<ServerPlayer> players) {
        if (region == Region.REDLINE && rescued) return;
        double cx = home.getX() + 0.5;
        double cz = home.getZ() + 0.5;
        for (ServerPlayer player : players) {
            if (Math.abs(player.getY() - home.getY()) > 2.5) continue;
            double x = player.getX();
            double z = player.getZ();
            boolean hit = switch (region) {
                case LASTLIGHT -> RegionalSentryPlan.axis(x, z,
                        alongX ? cx : targetX, alongX ? targetZ : cz,
                        alongX, 7.5, 1.0);
                case REHEARSAL -> RegionalSentryPlan.circle(x, z, targetX, targetZ, 2.2);
                case BRANCH -> RegionalSentryPlan.ray(x, z, cx, cz,
                        routeX, routeZ, 1, 8.5, 1.1);
                case REDLINE -> shelter != null && RegionalSentryPlan.ray(x, z,
                        cx, cz, routeX, routeZ, 0,
                        Math.max(Math.abs(shelter.getX() - home.getX()),
                                Math.abs(shelter.getZ() - home.getZ())) + 0.5, 1.1);
                case HUSHWIRE -> {
                    int[] offset = receiver(receiver);
                    boolean line = RegionalSentryPlan.ray(x, z, cx, cz,
                            Integer.signum(offset[0]), Integer.signum(offset[1]),
                            0, 4.5, 1.0);
                    yield line || RegionalSentryPlan.circle(x, z,
                            cx + offset[0], cz + offset[1], 2.0);
                }
                case UNWRITTEN -> switch (unwrittenAct) {
                    case SPOTLIGHT -> RegionalSentryPlan.circle(x, z,
                            targetX, targetZ, 2.2);
                    case CROSSCUT -> RegionalSentryPlan.axis(x, z,
                            cx, targetZ, true, 7.5, 1.0);
                    case CHORUS -> RegionalSentryPlan.circle(x, z, cx, cz, 5.0);
                };
            };
            if (hit) player.hurtServer(world, world.damageSources().mobAttack(this), 4.0F);
        }
    }

    private BlockPos findShelter(ServerLevel world) {
        for (int radius = 1; radius <= 8; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    BlockPos candidate = home.offset(dx, -1, dz);
                    if (world.getBlockState(candidate).is(Blocks.GOLD_BLOCK)) {
                        routeX = Integer.signum(dx);
                        routeZ = Integer.signum(dz);
                        return candidate.above();
                    }
                }
            }
        }
        return null;
    }

    private static int[] receiver(int index) {
        return switch (index) {
            case 0 -> new int[] {-4, 0};
            case 1 -> new int[] {4, 0};
            default -> new int[] {0, 4};
        };
    }

    private List<ServerPlayer> nearby(ServerLevel world) {
        return world.getEntitiesOfClass(ServerPlayer.class,
                new AABB(home).inflate(RANGE),
                player -> player.isAlive() && !player.isSpectator()
                        && player.distanceToSqr(Vec3.atCenterOf(home)) <= RANGE * RANGE);
    }

    private String hint() {
        return switch (region) {
            case LASTLIGHT -> "Gold track, orange lock. Leave the marked lane.";
            case REHEARSAL -> "The light follows you. Move once it stops.";
            case BRANCH -> "The cyan path predicts you. Change routes after the lock.";
            case REDLINE -> "Reach the gold tile before the red line closes.";
            case HUSHWIRE -> "Three calls. Wait for blue; leave its line.";
            case UNWRITTEN -> "The stage watches your habits. Follow the shape, then dodge.";
        };
    }

    private SoundEvent callSound() {
        return switch (region) {
            case LASTLIGHT -> AdventureSounds.LASTLIGHT_CALL;
            case REHEARSAL -> AdventureSounds.REHEARSAL_CALL;
            case BRANCH -> AdventureSounds.BRANCH_CALL;
            case REDLINE -> AdventureSounds.REDLINE_CALL;
            case HUSHWIRE -> AdventureSounds.HUSHWIRE_FALSE;
            case UNWRITTEN -> AdventureSounds.CADENZA_CALL;
        };
    }

    private SoundEvent lockSound() {
        return switch (region) {
            case LASTLIGHT -> AdventureSounds.LASTLIGHT_LOCK;
            case REHEARSAL -> AdventureSounds.REHEARSAL_LOCK;
            case BRANCH -> AdventureSounds.BRANCH_LOCK;
            case REDLINE -> AdventureSounds.REDLINE_LOCK;
            case HUSHWIRE -> AdventureSounds.HUSHWIRE_TRUE;
            case UNWRITTEN -> AdventureSounds.CADENZA_LOCK;
        };
    }

    private SoundEvent strikeSound() {
        return switch (region) {
            case LASTLIGHT -> AdventureSounds.LASTLIGHT_STRIKE;
            case REHEARSAL -> AdventureSounds.REHEARSAL_STRIKE;
            case BRANCH -> AdventureSounds.BRANCH_STRIKE;
            case REDLINE -> AdventureSounds.REDLINE_STRIKE;
            case HUSHWIRE -> AdventureSounds.HUSHWIRE_PULSE;
            case UNWRITTEN -> AdventureSounds.CADENZA_STRIKE;
        };
    }

    @Override
    protected void readAdditionalSaveData(ValueInput view) {
        super.readAdditionalSaveData(view);
        int x = view.getIntOr("HomeX", Integer.MIN_VALUE);
        int y = view.getIntOr("HomeY", Integer.MIN_VALUE);
        int z = view.getIntOr("HomeZ", Integer.MIN_VALUE);
        home = x == Integer.MIN_VALUE || y == Integer.MIN_VALUE || z == Integer.MIN_VALUE
                ? null : new BlockPos(x, y, z);
        shelter = null;
        shelterScanned = false;
        cycleTick = 0;
        targetId = null;
        hintSent = false;
        rescued = false;
        travel = 0;
        lastPositions.clear();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput view) {
        super.addAdditionalSaveData(view);
        if (home != null) {
            view.putInt("HomeX", home.getX());
            view.putInt("HomeY", home.getY());
            view.putInt("HomeZ", home.getZ());
        }
    }

    @Override
    public boolean isPersistenceRequired() {
        return true;
    }
}
