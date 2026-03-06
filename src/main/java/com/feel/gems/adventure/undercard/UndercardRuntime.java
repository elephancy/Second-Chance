package com.feel.gems.adventure.undercard;

import com.feel.gems.item.InventorySpace;
import com.feel.gems.sound.ModSounds;
import com.feel.gems.state.GemsPersistentDataHolder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

/** Server-owned optional bouts in a naturally placed Undercard instance. */
public final class UndercardRuntime {
    private static final int MAX_SESSIONS = 8;
    private static final int INVITE_TICKS = 30 * 20;
    private static final int MATCH_TICKS = 3 * 60 * 20;
    private static final int STRIKE_OPEN = 34;
    private static final int STRIKE_CLOSE = 62;
    private static final int ROUND_TICKS = 78;
    private static final String RECEIPTS = "UndercardReceipts";
    private static final DustParticleOptions TELL = new DustParticleOptions(0xE6A64D, 1.0F);
    private static final DustParticleOptions OPEN = new DustParticleOptions(0x7FDBCA, 1.1F);
    private static final Map<BlockPos, Solo> SOLO = new HashMap<>();
    private static final Map<BlockPos, Invitation> INVITES = new HashMap<>();
    private static final Map<BlockPos, Duel> DUELS = new HashMap<>();

    private static final class Solo {
        final UUID player;
        final BlockPos post;
        final UndercardConstructEntity construct;
        long roundStart;
        int strikes;
        boolean opening;

        Solo(UUID player, BlockPos post, UndercardConstructEntity construct, long start) {
            this.player = player;
            this.post = post;
            this.construct = construct;
            roundStart = start;
        }
    }

    private record Invitation(UUID challenger, long expiresAt) { }

    private static final class Duel {
        final UUID first;
        final UUID second;
        final BlockPos post;
        final long endsAt;
        long nextFirst;
        long nextSecond;
        int firstScore;
        int secondScore;

        Duel(UUID first, UUID second, BlockPos post, long endsAt) {
            this.first = first;
            this.second = second;
            this.post = post;
            this.endsAt = endsAt;
        }

        boolean contains(UUID player) {
            return first.equals(player) || second.equals(player);
        }
    }

    private UndercardRuntime() { }

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            BlockPos pos = hit.getBlockPos();
            Block block = world.getBlockState(pos).getBlock();
            if (block != UndercardBlocks.BELL && block != UndercardBlocks.SPARRING_POST
                    && block != UndercardBlocks.LEDGER && block != UndercardBlocks.EXIT) {
                return InteractionResult.PASS;
            }
            if (!(world instanceof ServerLevel level) || !(player instanceof ServerPlayer actor)) {
                return InteractionResult.SUCCESS;
            }
            if (level.dimension() != Level.OVERWORLD || !actor.isAlive() || actor.isSpectator()) {
                return InteractionResult.SUCCESS_SERVER;
            }
            if (block == UndercardBlocks.BELL) bell(level, actor, pos);
            else if (block == UndercardBlocks.SPARRING_POST) {
                tell(actor, "Dodge gold. Punch the construct on blue.");
            }
            else if (block == UndercardBlocks.LEDGER) {
                tell(actor, "Three winners, one fight. Ring the bell.");
            } else {
                cancel(actor.getUUID());
                tell(actor, "Follow the lit ladder to the quarry door above.");
            }
            return InteractionResult.SUCCESS_SERVER;
        });
        // A scored punch never reaches vanilla damage or on-hit gem effects.
        AttackEntityCallback.EVENT.register((attacker, world, hand, entity, hit) -> {
            if (world.isClientSide() || hand != InteractionHand.MAIN_HAND
                    || !(attacker instanceof ServerPlayer striker)) return InteractionResult.PASS;
            if (entity instanceof UndercardConstructEntity construct) {
                if (striker.level() instanceof ServerLevel level) {
                    counter(level, striker, construct);
                }
                return InteractionResult.FAIL;
            }
            if (!(entity instanceof ServerPlayer target)) return InteractionResult.PASS;
            Duel duel = duelFor(striker.getUUID());
            if (duel == null || !duel.contains(target.getUUID())) return InteractionResult.PASS;
            if (striker.level() instanceof ServerLevel level) {
                score(level, striker, target, duel);
            }
            return InteractionResult.FAIL;
        });
        // Gem abilities, projectiles, thorns and outside players cannot turn
        // a consented nonlethal bout into an accidental death.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof UndercardConstructEntity) return false;
            if (entity instanceof ServerPlayer player) {
                Duel duel = duelFor(player.getUUID());
                if (duel != null) return false;
            }
            return true;
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                deliverPending(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                cancel(handler.getPlayer().getUUID()));
        ServerTickEvents.END_SERVER_TICK.register(UndercardRuntime::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (Solo solo : SOLO.values()) solo.construct.discard();
            SOLO.clear();
            INVITES.clear();
            DUELS.clear();
        });
    }

    private static void bell(ServerLevel level, ServerPlayer actor, BlockPos bell) {
        BlockPos post = postNearBell(level, bell);
        if (post == null) {
            tell(actor, "The sparring post is missing. This ring is out of service.");
            return;
        }
        if (active(actor.getUUID())) {
            tell(actor, "Finish or leave your current bout first.");
            return;
        }
        if (SOLO.containsKey(bell) || DUELS.containsKey(bell)) {
            tell(actor, "This ring is in use. You can watch from the gallery.");
            return;
        }
        long now = level.getServer().getTickCount();
        if (actor.isShiftKeyDown()) {
            Invitation invitation = INVITES.get(bell);
            if (invitation != null && invitation.expiresAt() >= now
                    && !invitation.challenger().equals(actor.getUUID())) {
                ServerPlayer challenger = level.getServer().getPlayerList().getPlayer(invitation.challenger());
                if (challenger != null && challenger.level() == level && challenger.isAlive()
                        && !challenger.isSpectator() && !active(challenger.getUUID())
                        && near(challenger, bell, 12)) {
                    if (SOLO.size() + DUELS.size() >= MAX_SESSIONS) {
                        tell(actor, "The ring lights are busy. Try again shortly.");
                        return;
                    }
                    INVITES.remove(bell);
                    DUELS.put(bell.immutable(), new Duel(challenger.getUUID(), actor.getUUID(),
                            post, now + MATCH_TICKS));
                    tell(challenger, "Three clean punches win. No damage here.");
                    tell(actor, "Three clean punches win. No damage here.");
                    chime(level, bell);
                    return;
                }
            }
            INVITES.put(bell.immutable(), new Invitation(actor.getUUID(), now + INVITE_TICKS));
            tell(actor, "Duel offered. Another player sneaks here in 30s.");
            return;
        }
        if (SOLO.size() + DUELS.size() >= MAX_SESSIONS) {
            tell(actor, "The ring lights are busy. Try again shortly.");
            return;
        }
        INVITES.remove(bell);
        UndercardConstructEntity construct = UndercardEntities.CONSTRUCT.create(level,
                EntitySpawnReason.EVENT);
        if (construct == null) {
            tell(actor, "The clockwork construct did not wake. Try the bell again.");
            return;
        }
        // This scripted bout starts with a fully prepared construct.
        construct.setHealth(construct.getMaxHealth());
        BlockPos lane = lane(post, 0);
        if (!safeLane(level, lane)) {
            tell(actor, "The ring floor is blocked. This bout cannot start.");
            return;
        }
        construct.snapTo(lane.getX() + 0.5, lane.getY(), lane.getZ() + 0.5,
                0.0F, 0.0F);
        if (!level.addFreshEntity(construct)) {
            tell(actor, "The clockwork construct did not wake. Try the bell again.");
            return;
        }
        SOLO.put(bell.immutable(), new Solo(actor.getUUID(), post, construct, now));
        tell(actor, "Dodge gold. Punch on blue. Win three rounds.");
        chime(level, bell);
    }

    private static void counter(ServerLevel level, ServerPlayer actor,
                                UndercardConstructEntity construct) {
        Solo session = soloForConstruct(construct.getUUID());
        if (session == null || !session.player.equals(actor.getUUID())
                || !insideRing(actor, session.post)
                || actor.distanceToSqr(construct) > 4.5 * 4.5) return;
        long now = level.getServer().getTickCount();
        int elapsed = (int) (now - session.roundStart);
        if (!session.opening || elapsed < STRIKE_OPEN || elapsed > STRIKE_CLOSE) {
            tell(actor, "Wait for blue, then counter the construct itself.");
            return;
        }
        session.strikes++;
        chime(level, construct.blockPosition());
        if (session.strikes == 3) {
            BlockPos bell = bellForSolo(session);
            if (bell == null) return;
            SOLO.remove(bell);
            construct.discard();
            finish(level, bell, Set.of(actor.getUUID()));
            tell(actor, "Three counters. You won the match.");
        } else {
            session.roundStart = now + 10;
            session.opening = false;
            BlockPos next = lane(session.post, session.strikes);
            if (!safeLane(level, next)) {
                BlockPos bell = bellForSolo(session);
                if (bell != null) SOLO.remove(bell);
                construct.discard();
                tell(actor, "The next ring lane is blocked. This bout ended safely.");
                return;
            }
            construct.snapTo(next.getX() + 0.5, next.getY(), next.getZ() + 0.5,
                    construct.getYRot() + 90.0F, 0.0F);
            tell(actor, "Counter " + session.strikes + "/3. The construct shifted lanes.");
        }
    }

    private static void score(ServerLevel level, ServerPlayer striker, ServerPlayer target, Duel duel) {
        BlockPos bell = bellFor(duel);
        if (bell == null || !insideRing(striker, duel.post) || !insideRing(target, duel.post)
                || striker.distanceToSqr(target) > 4.5 * 4.5) {
            tell(striker, "Return to the lit ring to score.");
            return;
        }
        long now = level.getServer().getTickCount();
        boolean first = striker.getUUID().equals(duel.first);
        if (now < (first ? duel.nextFirst : duel.nextSecond)) return;
        if (first) {
            duel.firstScore++;
            duel.nextFirst = now + 16;
        } else {
            duel.secondScore++;
            duel.nextSecond = now + 16;
        }
        chime(level, duel.post);
        ServerPlayer other = level.getServer().getPlayerList().getPlayer(
                first ? duel.second : duel.first);
        String update = "Undercard: " + duel.firstScore + "–" + duel.secondScore + ". First to three.";
        tell(striker, update);
        if (other != null) tell(other, update);
        if (duel.firstScore >= 3 || duel.secondScore >= 3) {
            DUELS.remove(bell);
            finish(level, bell, Set.of(duel.firstScore >= 3 ? duel.first : duel.second));
            tell(striker, "Three bells. The match is over.");
            if (other != null) tell(other, "Three bells. The match is over.");
        }
    }

    private static void tick(MinecraftServer server) {
        ServerLevel level = server.getLevel(Level.OVERWORLD);
        if (level == null) return;
        long now = server.getTickCount();
        if (now % 20 == 0) {
            INVITES.entrySet().removeIf(entry -> entry.getValue().expiresAt() < now
                    || server.getPlayerList().getPlayer(entry.getValue().challenger()) == null);
        }
        Iterator<Map.Entry<BlockPos, Solo>> solos = SOLO.entrySet().iterator();
        while (solos.hasNext()) {
            Map.Entry<BlockPos, Solo> entry = solos.next();
            BlockPos bell = entry.getKey();
            Solo session = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(session.player);
            if (player == null || player.level() != level || !player.isAlive()
                    || player.isSpectator() || !insideRing(player, session.post)
                    || !session.construct.isAlive() || session.construct.level() != level
                    || !level.hasChunkAt(bell) || !level.getBlockState(bell).is(UndercardBlocks.BELL)) {
                session.construct.discard();
                solos.remove();
                continue;
            }
            int elapsed = (int) (now - session.roundStart);
            if (elapsed >= ROUND_TICKS) {
                session.roundStart = now + 10;
                session.opening = false;
                tell(player, "Opening closed. Dodge, then counter.");
            } else if (elapsed >= 0 && elapsed % 6 == 0) {
                BlockPos target = session.construct.blockPosition();
                ring(level, target, elapsed >= STRIKE_OPEN ? OPEN : TELL);
                if (elapsed == STRIKE_OPEN + 2) {
                    // Standing in the marked sweep costs no health but requires
                    // another tell. Position matters even when click timing is good.
                    if (player.distanceToSqr(session.construct) <= 2.7 * 2.7) {
                        session.roundStart = now + 10;
                        session.opening = false;
                        tell(player, "Gold sweep hit. Step clear; strike on blue.");
                    } else {
                        session.opening = true;
                        tell(player, "Blue light. Close in and punch the construct.");
                        chime(level, target);
                    }
                }
            }
        }
        DUELS.entrySet().removeIf(entry -> {
            Duel duel = entry.getValue();
            ServerPlayer first = server.getPlayerList().getPlayer(duel.first);
            ServerPlayer second = server.getPlayerList().getPlayer(duel.second);
            boolean invalid = now >= duel.endsAt || first == null || second == null
                    || first.level() != level || second.level() != level
                    || !first.isAlive() || !second.isAlive() || first.isSpectator()
                    || second.isSpectator() || !insideRing(first, duel.post)
                    || !insideRing(second, duel.post) || !level.hasChunkAt(entry.getKey())
                    || !level.getBlockState(entry.getKey()).is(UndercardBlocks.BELL);
            if (invalid) {
                if (first != null) tell(first, "The Undercard bout ended without a result.");
                if (second != null) tell(second, "The Undercard bout ended without a result.");
            }
            return invalid;
        });
        if (now % 100 == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) deliverPending(player);
        }
    }

    private static void finish(ServerLevel level, BlockPos bell, Set<UUID> participants) {
        UndercardState state = UndercardState.get(level.getServer());
        state.complete(bell.asLong(), participants);
        for (UUID id : participants) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player != null) deliverPending(player);
        }
    }

    private static void deliverPending(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator()) return;
        UndercardState state = UndercardState.get(player.level().getServer());
        CompoundTag root = ((GemsPersistentDataHolder) player).gems$getPersistentData();
        CompoundTag receipts = root.getCompound(RECEIPTS).orElseGet(() -> {
            CompoundTag created = new CompoundTag();
            root.put(RECEIPTS, created);
            return created;
        });
        for (long site : state.earned(player.getUUID()).stream().sorted().toList()) {
            String key = Long.toString(site);
            if (receipts.getBooleanOr(key, false)) continue;
            ItemStack trophy = new ItemStack(Items.COPPER_INGOT);
            trophy.set(DataComponents.CUSTOM_NAME, Component.literal("Undercard Winner's Brass"));
            if (!InventorySpace.insertWhole(player, trophy)) return;
            // The receipt and item save in the same playerdata file.
            receipts.putBoolean(key, true);
            tell(player, "Winner's brass collected. The bout is saved to this world.");
        }
    }

    private static boolean active(UUID player) {
        return SOLO.values().stream().anyMatch(session -> session.player.equals(player))
                || duelFor(player) != null;
    }

    public static boolean hasActiveConstruct(UUID entity) {
        return soloForConstruct(entity) != null;
    }

    private static Solo soloForConstruct(UUID entity) {
        for (Solo session : SOLO.values()) {
            if (session.construct.getUUID().equals(entity)) return session;
        }
        return null;
    }

    private static BlockPos bellForSolo(Solo solo) {
        for (Map.Entry<BlockPos, Solo> entry : SOLO.entrySet()) {
            if (entry.getValue() == solo) return entry.getKey();
        }
        return null;
    }

    private static Duel duelFor(UUID player) {
        for (Duel duel : DUELS.values()) if (duel.contains(player)) return duel;
        return null;
    }

    private static BlockPos bellFor(Duel duel) {
        for (Map.Entry<BlockPos, Duel> entry : DUELS.entrySet()) {
            if (entry.getValue() == duel) return entry.getKey();
        }
        return null;
    }

    private static void cancel(UUID player) {
        SOLO.entrySet().removeIf(entry -> {
            if (!entry.getValue().player.equals(player)) return false;
            entry.getValue().construct.discard();
            return true;
        });
        INVITES.entrySet().removeIf(entry -> entry.getValue().challenger().equals(player));
        DUELS.entrySet().removeIf(entry -> entry.getValue().contains(player));
    }

    private static BlockPos postNearBell(ServerLevel level, BlockPos bell) {
        for (BlockPos candidate : Set.of(bell.offset(8, 0, 0), bell.offset(-8, 0, 0),
                bell.offset(0, 0, 8), bell.offset(0, 0, -8))) {
            if (level.hasChunkAt(candidate)
                    && level.getBlockState(candidate).is(UndercardBlocks.SPARRING_POST)) return candidate;
        }
        return null;
    }

    private static BlockPos lane(BlockPos post, int round) {
        return switch (round) {
            case 0 -> post.offset(-5, 0, 0);
            case 1 -> post.offset(5, 0, 0);
            default -> post.offset(0, 0, 5);
        };
    }

    private static boolean safeLane(ServerLevel level, BlockPos lane) {
        return level.hasChunkAt(lane) && level.getBlockState(lane).isAir()
                && level.getBlockState(lane.above()).isAir()
                && level.getBlockState(lane.below()).isSolid()
                && level.getFluidState(lane).isEmpty();
    }

    private static boolean insideRing(ServerPlayer player, BlockPos post) {
        return player.level().dimension() == Level.OVERWORLD && player.isAlive()
                && Math.abs(player.getY() - post.getY()) <= 5
                && player.distanceToSqr(Vec3.atCenterOf(post)) <= 13 * 13;
    }

    private static boolean near(ServerPlayer player, BlockPos pos, int radius) {
        return player.distanceToSqr(Vec3.atCenterOf(pos)) <= radius * radius;
    }

    private static void ring(ServerLevel level, BlockPos post, DustParticleOptions dust) {
        for (int i = 0; i < 12; i++) {
            double angle = Math.PI * 2 * i / 12;
            level.sendParticles(dust, post.getX() + 0.5 + Math.cos(angle) * 2.7,
                    post.getY() + 0.2, post.getZ() + 0.5 + Math.sin(angle) * 2.7,
                    1, 0, 0, 0, 0);
        }
    }

    private static void chime(ServerLevel level, BlockPos pos) {
        level.playSound(null, pos, ModSounds.ARCANE_CHIME, SoundSource.BLOCKS, 0.65F, 0.9F);
    }

    private static void tell(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message), true);
    }
}
