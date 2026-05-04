package com.feel.gems.gametest.adventure;

import com.feel.gems.adventure.undercard.UndercardBlocks;
import com.feel.gems.adventure.undercard.UndercardConstructEntity;
import com.feel.gems.adventure.undercard.UndercardRuntime;
import com.feel.gems.adventure.undercard.UndercardState;
import com.feel.gems.state.GemsPersistentDataHolder;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Opt-in connected route and nonlethal duel on a copied accepted V5 world. */
public final class UndercardConnectedServerSmoke implements ModInitializer {
    private static final Logger LOG = LoggerFactory.getLogger("gems-undercard-connected-server");
    private static final BlockPos GATE = new BlockPos(-701, 64, -1135);
    private static final BlockPos BELL = new BlockPos(-731, 36, -1135);
    private static final BlockPos POST = new BlockPos(-723, 36, -1135);
    private static final BlockPos EXIT = new BlockPos(-706, 36, -1133);
    private String phase;
    private String stage = "boot";
    private ServerPlayer a;
    private ServerPlayer b;
    private int deadline;
    private int scoreAt;
    private int cleanupAt;
    private int score;
    private int descentAt;
    private int ascentAt;
    private int movementConfirmBy;
    private int ringConfirmBy;
    private double lowestDescentY;
    private float healthA;
    private float healthB;
    private Vec3 duelBPosition;

    @Override public void onInitialize() {
        if (!Boolean.getBoolean("gems.undercardConnected.server")) return;
        phase = System.getProperty("gems.undercardConnected.phase", "");
        if (!phase.equals("initial") && !phase.equals("restart"))
            throw new IllegalArgumentException("Undercard connected phase required");
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
                dispatcher.register(Commands.literal("udc")
                        .then(Commands.argument("stage", StringArgumentType.word())
                                .executes(context -> ack(context.getSource().getServer(),
                                        context.getSource().getPlayer(),
                                        StringArgumentType.getString(context, "stage"))))));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                join(server, handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                disconnect(server, handler.getPlayer()));
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, amount, taken, blocked) -> {
            if (entity == a || entity == b) {
                LOG.info("UDC_DAMAGE_RESULT stage={} tick={} target={} source={} amount={} taken={} blocked={} health={}",
                        stage, entity.level().getServer().getTickCount(),
                        entity == a ? "A" : "B", source, amount, taken, blocked, entity.getHealth());
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
    }

    private void tick(MinecraftServer server) {
        if (stage.equals("boot") && server.getTickCount() >= 100) {
            ServerLevel level = server.overworld();
            if (!level.getBlockState(GATE).is(Blocks.LADDER)
                    || !level.getBlockState(BELL).is(UndercardBlocks.BELL)
                    || !level.getBlockState(POST).is(UndercardBlocks.SPARRING_POST)
                    || !level.getBlockState(EXIT).is(UndercardBlocks.EXIT)) {
                fail(server, "copied V5 markers or opened ladder missing");
                return;
            }
            stage = "await-a";
            deadline = server.getTickCount() + 20 * 300;
            LOG.info("UDC_READY_A phase={} savedSite=true", phase);
        }
        if (server.getTickCount() > deadline && !stage.equals("boot")
                && !stage.equals("complete") && !stage.equals("failed"))
            fail(server, "connected stage timed out: " + stage);
        if (stage.equals("offer-confirm")) {
            if (ringState("INVITES") && !ringState("SOLO") && !ringState("DUELS")) {
                LOG.info("UDC_INVITE_PASS realBellPacket=true");
                stage = "accept-ready";
                ringConfirmBy = server.getTickCount() + 100;
            } else if (ringState("SOLO") || ringState("DUELS")
                    || server.getTickCount() > ringConfirmBy) {
                fail(server, "real bell offer did not create invitation; invite="
                        + ringState("INVITES") + " solo=" + ringState("SOLO")
                        + " duel=" + ringState("DUELS"));
            }
        }
        if (stage.equals("accept-ready")) {
            if (b != null && b.connection.hasClientLoaded() && !awaitingTeleport(b)
                    && b.distanceToSqr(Vec3.atCenterOf(BELL)) <= 4.5 * 4.5) {
                LOG.info("UDC_ACCEPT_READY serverClientLoaded=true teleportAcknowledged=true pos={}",
                        b.position());
                stage = "accept";
                send(b, "UDC|USE|accept");
            } else if (server.getTickCount() > ringConfirmBy) {
                fail(server, "B not ready for bell packet; clientLoaded="
                        + (b != null && b.connection.hasClientLoaded()) + " pending="
                        + (b != null && awaitingTeleport(b)) + " pos="
                        + (b == null ? "absent" : b.position()));
            }
        }
        if (stage.equals("accept-confirm")) {
            if (ringState("DUELS") && !ringState("INVITES") && !ringState("SOLO")) {
                LOG.info("UDC_DUEL_STARTED realBellPackets=true");
                duelBPosition = b.position();
                stage = "scoring";
                scoreAt = server.getTickCount();
                send(a, "UDC|SCORE|" + b.getId());
            } else if (ringState("SOLO") || server.getTickCount() > ringConfirmBy) {
                fail(server, "real bell accept did not create duel; invite="
                        + ringState("INVITES") + " solo=" + ringState("SOLO")
                        + " duel=" + ringState("DUELS"));
            }
        }
        if ((stage.equals("descend") || stage.equals("descent-confirm")) && a != null) {
            if (Math.abs(a.getX() - GATE.getX() - .5) > 1.2
                    || Math.abs(a.getZ() - GATE.getZ() - .5) > 1.2) {
                fail(server, "descent left the backed shaft"); return;
            }
            lowestDescentY = Math.min(lowestDescentY, a.getY());
        }
        if (stage.equals("descent-confirm")) {
            if (a.getY() <= 37.8 && lowestDescentY <= 37.8
                    && server.getTickCount() - descentAt >= 20) {
                LOG.info("UDC_DESCENT_PASS normalInput=true sameShaft=true top={} bottom={} ticks={}",
                        GATE.getY() + 1, a.getY(), server.getTickCount() - descentAt);
                List<BlockPos> path = path(server.overworld(), shaftFeet(), BELL.east());
                if (path.isEmpty()) { fail(server, "no walkable shaft-to-bell path"); return; }
                stage = "walk-in";
                send(a, "UDC|WALK|in|" + encode(path));
            } else if (server.getTickCount() > movementConfirmBy) {
                fail(server, "server never observed full same-shaft descent, y=" + a.getY()
                        + " lowest=" + lowestDescentY);
            }
        }
        if (stage.equals("scoring") && server.getTickCount() >= scoreAt) {
            boolean earned = UndercardState.get(server).hasEarned(a.getUUID(), BELL.asLong());
            if (earned) {
                if (!sameHealth() || !winnerReceipt(a) || !loserClear(b)
                        || UndercardState.get(server).hasEarned(b.getUUID(), BELL.asLong())) {
                    fail(server, "duel damage, loser reward, or missing winner receipt"); return;
                }
                LOG.info("UDC_DUEL_PASS twoConnectedPlayers=true nonlethal=true winnerOnly=true brassCount=1");
                List<BlockPos> path = path(server.overworld(), BELL.east(), shaftFeet());
                if (path.isEmpty()) { fail(server, "no walkable bell-to-shaft path"); return; }
                stage = "walk-out";
                send(a, "UDC|WALK|out|" + encode(path));
            } else if (server.getTickCount() - scoreAt > 20 * 90) {
                fail(server, "three scored hits did not award the winner");
            }
        }
        if ((stage.equals("ascend") || stage.equals("ascent-confirm")) && a != null
                && (Math.abs(a.getX() - GATE.getX() - .5) > 1.2
                    || Math.abs(a.getZ() - GATE.getZ() - .5) > 1.2)) {
            fail(server, "return climb left the backed shaft");
        }
        if (stage.equals("ascent-confirm")) {
            if (a.getY() >= GATE.getY() - .5
                    && server.getTickCount() - ascentAt >= 20) {
                LOG.info("UDC_RETURN_PASS normalInput=true sameShaft=true y={} gateY={} ticks={}",
                        a.getY(), GATE.getY(), server.getTickCount() - ascentAt);
                stage = "cleanup-ready";
                cleanupAt = server.getTickCount() + 100;
                a.teleportTo(server.overworld(), BELL.getX() + 1.5, BELL.getY(),
                        BELL.getZ() + .5, Set.of(), 0, 0, true);
            } else if (server.getTickCount() > movementConfirmBy) {
                fail(server, "server never observed full same-shaft return, y=" + a.getY());
            }
        }
        if (stage.equals("cleanup-ready")) {
            if (a.connection.hasClientLoaded() && !awaitingTeleport(a)
                    && a.distanceToSqr(Vec3.atCenterOf(BELL)) <= 4.5 * 4.5) {
                stage = "cleanup-offer";
                LOG.info("UDC_CLEANUP_READY serverClientLoaded=true teleportAcknowledged=true pos={}",
                        a.position());
                send(a, "UDC|USE|cleanup-offer");
            } else if (server.getTickCount() > cleanupAt) {
                fail(server, "A not ready for cleanup bell packet; clientLoaded="
                        + a.connection.hasClientLoaded() + " pending="
                        + awaitingTeleport(a) + " pos=" + a.position());
            }
        }
        if (stage.equals("cleanup-offer-confirm")) {
            if (ringState("INVITES") && !ringState("DUELS") && !ringState("SOLO")) {
                stage = "cleanup-accept";
                LOG.info("UDC_CLEANUP_INVITE_PASS realBellPacket=true");
                send(b, "UDC|USE|cleanup-accept");
            } else if (ringState("DUELS") || ringState("SOLO")
                    || server.getTickCount() > ringConfirmBy) {
                fail(server, "second real bell offer did not create invitation");
            }
        }
        if (stage.equals("cleanup-accept-confirm")) {
            if (ringState("DUELS") && !ringState("INVITES") && !ringState("SOLO")) {
                stage = "cleanup-disconnect";
                LOG.info("UDC_CLEANUP_DUEL_PASS realBellPackets=true");
                send(b, "UDC|DISCONNECT");
            } else if (ringState("SOLO") || server.getTickCount() > ringConfirmBy) {
                fail(server, "second real bell accept did not create duel");
            }
        }
        if (stage.equals("cleanup-check")) {
            if (!ringState("DUELS") && !ringState("INVITES") && !ringState("SOLO")
                    && winnerReceipt(a)) {
                stage = "cleanup-use";
                LOG.info("UDC_DISCONNECT_PASS activeDuelReleased=true winnerReceiptKept=true");
                send(a, "UDC|USE|solo-after-disconnect");
            } else if (server.getTickCount() > cleanupAt) {
                fail(server, "disconnect left the active duel or altered winner receipt");
            }
        }
        if (stage.equals("cleanup-solo-confirm")) {
            AABB arena = new AABB(BELL).inflate(18);
            if (ringState("SOLO") && !ringState("DUELS")
                    && server.overworld().getEntitiesOfClass(
                            UndercardConstructEntity.class, arena).size() == 1
                    && winnerReceipt(a)) {
                LOG.info("UDC_CANCEL_PASS disconnectedDuelReleasedBell=true savedWinnerSingle=true");
                complete();
            } else if (server.getTickCount() > ringConfirmBy) {
                fail(server, "real solo bell use did not reopen ring or winner receipt changed");
            }
        }
    }

    private void join(MinecraftServer server, ServerPlayer player) {
        if (stage.equals("boot") || stage.equals("failed") || stage.equals("complete")) {
            fail(server, "unexpected join in " + stage); return;
        }
        String name = player.getName().getString();
        if (name.equals("UndercardA")) {
            if (a != null || !stage.equals("await-a")) {
                fail(server, "duplicate or out-of-order A"); return;
            }
            a = player;
            if (phase.equals("restart")) {
                if (!UndercardState.get(server).hasEarned(a.getUUID(), BELL.asLong())
                        || !winnerReceipt(a)) {
                    fail(server, "winner world/player receipt lost on restart"); return;
                }
                stage = "await-b";
                LOG.info("UDC_READY_B phase=restart savedWinner=true");
            } else {
                if (UndercardState.get(server).hasEarned(a.getUUID(), BELL.asLong())
                        || winnerReceipt(a)) {
                    fail(server, "fresh duel player already rewarded"); return;
                }
                stage = "prepare-descend";
                send(player, "UDC|PREPARE");
            }
        } else if (name.equals("UndercardB")) {
            if (b != null || !stage.equals("await-b")) {
                fail(server, "duplicate or out-of-order B"); return;
            }
            b = player;
            if (!loserClear(b) || UndercardState.get(server).hasEarned(b.getUUID(), BELL.asLong())) {
                fail(server, "loser acquired winner receipt"); return;
            }
            if (phase.equals("restart")) {
                if (!server.overworld().getEntitiesOfClass(UndercardConstructEntity.class,
                        new AABB(BELL).inflate(18)).isEmpty()) {
                    fail(server, "transient solo construct survived restart"); return;
                }
                LOG.info("UDC_RESTART_PASS twoConnectedPlayers=true winnerPersistent=true loserClear=true gateOpen=true");
                complete();
            } else {
                player.teleportTo(server.overworld(), BELL.getX() + 2.5,
                        BELL.getY(), BELL.getZ() + .5, Set.of(), 0, 0, true);
                healthA = a.getHealth(); healthB = b.getHealth();
                stage = "offer";
                send(a, "UDC|USE|offer");
            }
        } else fail(server, "unexpected connected player " + name);
    }

    private int ack(MinecraftServer server, ServerPlayer player, String response) {
        if (stage.equals("prepare-descend") && player == a
                && response.equals("prepared")) {
            player.teleportTo(server.overworld(), GATE.getX() + .5,
                    GATE.getY() + 1.0, GATE.getZ() + .5, Set.of(), 0, 0, true);
            stage = "descend";
            descentAt = server.getTickCount();
            lowestDescentY = GATE.getY() + 1;
            send(player, "UDC|DESCEND|" + GATE.getY());
        } else if (stage.equals("descend") && player == a
                && response.equals("descended")) {
            stage = "descent-confirm";
            movementConfirmBy = server.getTickCount() + 60;
        } else if (stage.equals("walk-in") && player == a && response.equals("walk-in")) {
            if (a.distanceToSqr(Vec3.atCenterOf(BELL.east())) > 2.5 * 2.5) {
                fail(server, "client claimed entrance path without reaching bell"); return 0;
            }
            LOG.info("UDC_WALK_IN_PASS normalInput=true nearBell=true");
            stage = "await-b";
            LOG.info("UDC_READY_B phase=initial");
        } else if (stage.equals("offer") && player == a && response.equals("offer")) {
            stage = "offer-confirm";
            ringConfirmBy = server.getTickCount() + 40;
        } else if (stage.equals("accept") && player == b && response.equals("accept")) {
            stage = "accept-confirm";
            ringConfirmBy = server.getTickCount() + 40;
        } else if (stage.equals("scoring") && player == a && response.equals("hit")) {
            score++;
            if (score > 3 || !sameHealth() || b.position().distanceToSqr(duelBPosition) > .75 * .75) {
                fail(server, "duel attack damaged either player or exceeded three hits"
                        + " score=" + score + " A=" + (a == null ? "absent" : a.getHealth())
                        + "/" + healthA + " B=" + (b == null ? "absent" : b.getHealth())
                        + "/" + healthB + " BDisplacement="
                        + b.position().distanceTo(duelBPosition)); return 0;
            }
            LOG.info("UDC_HIT score={} protectedHealth=true", score);
        } else if (stage.equals("walk-out") && player == a && response.equals("walk-out")) {
            if (a.distanceToSqr(Vec3.atCenterOf(shaftFeet())) > 2.5 * 2.5) {
                fail(server, "client claimed return path away from shaft"); return 0;
            }
            LOG.info("UDC_WALK_OUT_PASS normalInput=true nearShaft=true");
            stage = "ascend";
            ascentAt = server.getTickCount();
            send(a, "UDC|ASCEND|" + GATE.getY());
        } else if (stage.equals("ascend") && player == a
                && response.equals("ascended")) {
            stage = "ascent-confirm";
            movementConfirmBy = server.getTickCount() + 60;
        } else if (stage.equals("cleanup-offer") && player == a
                && response.equals("cleanup-offer")) {
            stage = "cleanup-offer-confirm";
            ringConfirmBy = server.getTickCount() + 40;
        } else if (stage.equals("cleanup-accept") && player == b
                && response.equals("cleanup-accept")) {
            stage = "cleanup-accept-confirm";
            ringConfirmBy = server.getTickCount() + 40;
        } else if (stage.equals("cleanup-use") && player == a
                && response.equals("solo-after-disconnect")) {
            stage = "cleanup-solo-confirm";
            ringConfirmBy = server.getTickCount() + 40;
        } else {
            fail(server, "out-of-order client acknowledgement " + response
                    + " from " + player.getName().getString() + " in " + stage);
            return 0;
        }
        return 1;
    }

    private void disconnect(MinecraftServer server, ServerPlayer player) {
        if (stage.equals("cleanup-disconnect") && player == b) {
            b = null;
            stage = "cleanup-check";
            cleanupAt = server.getTickCount() + 40;
            LOG.info("UDC_DISCONNECT_OBSERVED player=B");
        } else if (!stage.equals("complete") && !stage.equals("failed")) {
            fail(server, "unexpected client disconnect during " + stage);
        }
    }

    private boolean sameHealth() {
        return a != null && b != null && a.getHealth() == healthA && b.getHealth() == healthB;
    }

    private static boolean ringState(String fieldName) {
        try {
            Field field = UndercardRuntime.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return ((Map<?, ?>) field.get(null)).containsKey(BELL);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Undercard ring state unavailable: " + fieldName, error);
        }
    }

    private static boolean awaitingTeleport(ServerPlayer player) {
        try {
            Field field = ServerGamePacketListenerImpl.class
                    .getDeclaredField("awaitingPositionFromClient");
            field.setAccessible(true);
            return field.get(player.connection) != null;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("server teleport acknowledgement unavailable", error);
        }
    }

    private static boolean winnerReceipt(ServerPlayer player) {
        return player != null && hasReceipt(player) && brassCount(player) == 1;
    }

    private static boolean loserClear(ServerPlayer player) {
        return player != null && !hasReceipt(player) && brassCount(player) == 0;
    }

    private static boolean hasReceipt(ServerPlayer player) {
        if (player == null) return false;
        var receipts = ((GemsPersistentDataHolder) player).gems$getPersistentData()
                .getCompound("UndercardReceipts");
        return receipts.isPresent()
                && receipts.get().getBooleanOr(Long.toString(BELL.asLong()), false);
    }

    private static int brassCount(ServerPlayer player) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            var stack = player.getInventory().getItem(i);
            var name = stack.get(DataComponents.CUSTOM_NAME);
            if (stack.is(Items.COPPER_INGOT) && name != null
                    && name.getString().equals("Undercard Winner's Brass")) count += stack.getCount();
        }
        return count;
    }

    private static List<BlockPos> path(ServerLevel level, BlockPos from, BlockPos to) {
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Map<BlockPos, BlockPos> previous = new HashMap<>();
        queue.add(from); previous.put(from, from);
        while (!queue.isEmpty() && previous.size() <= 63 * 63) {
            BlockPos current = queue.removeFirst();
            if (current.equals(to)) break;
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos next = current.relative(direction);
                if (Math.abs(next.getX() - GATE.getX()) > 48
                        || Math.abs(next.getZ() - GATE.getZ()) > 48
                        || previous.containsKey(next)
                        || (!next.equals(to) && !walkable(level, next))) continue;
                previous.put(next, current); queue.add(next);
            }
        }
        if (!previous.containsKey(to)) return List.of();
        ArrayList<BlockPos> result = new ArrayList<>();
        for (BlockPos p = to; !p.equals(from); p = previous.get(p)) result.add(p);
        result.add(from); java.util.Collections.reverse(result);
        return result;
    }

    private static boolean walkable(ServerLevel level, BlockPos feet) {
        return level.getBlockState(feet.below()).isSolid()
                && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
                && level.getFluidState(feet).isEmpty();
    }

    private static BlockPos shaftFeet() {
        return new BlockPos(GATE.getX(), BELL.getY(), GATE.getZ());
    }

    private static String encode(List<BlockPos> path) {
        StringBuilder out = new StringBuilder();
        for (BlockPos pos : path) {
            if (!out.isEmpty()) out.append(';');
            out.append(pos.getX()).append(',').append(pos.getY()).append(',').append(pos.getZ());
        }
        return out.toString();
    }

    private static void send(ServerPlayer player, String instruction) {
        player.sendSystemMessage(Component.literal(instruction));
    }

    private void complete() {
        stage = "complete";
        send(a, "UDC|COMPLETE|" + phase);
        if (b != null) send(b, "UDC|COMPLETE|" + phase);
        LOG.info("UDC_PHASE_PASS phase={} venueSaved=true", phase);
    }

    private void fail(MinecraftServer server, String reason) {
        if (stage.equals("failed")) return;
        stage = "failed";
        LOG.error("UDC_PHASE_FAIL phase={} reason={}", phase, reason);
        if (a != null) send(a, "UDC|FAIL|" + reason);
        if (b != null) send(b, "UDC|FAIL|" + reason);
    }
}
