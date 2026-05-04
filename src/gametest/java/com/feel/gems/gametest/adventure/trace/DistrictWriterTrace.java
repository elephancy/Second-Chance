package com.feel.gems.gametest.adventure.trace;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Test-only record of actual worldgen writes attributable to the two staged districts. */
public final class DistrictWriterTrace {
    private static final String PROPERTY = "gems.districtWriterTraceConfig";
    private static final boolean ENABLED = !System.getProperty(PROPERTY, "").isBlank();
    private static final int MAX_EVENTS = 1_000_000;
    private static final ConcurrentLinkedQueue<String> EVENTS = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger COUNT = new AtomicInteger();
    private static final Config CONFIG = ENABLED ? readConfig() : null;
    private static final ThreadLocal<ArrayDeque<Piece>> PIECES =
            ThreadLocal.withInitial(ArrayDeque::new);

    static {
        if (ENABLED) {
            Runtime.getRuntime().addShutdownHook(new Thread(DistrictWriterTrace::flush,
                    "district-writer-trace-flush"));
        }
    }

    private DistrictWriterTrace() { }

    public static boolean enabled() { return ENABLED; }

    public static boolean beginPiece(String template, BoundingBox box) {
        if (!ENABLED || !CONFIG.templates().contains(template)) return false;
        String target = template.startsWith("gems:hushwire_")
                ? "hushwire" : "othman_library";
        PIECES.get().push(new Piece(template, target, box.toString()));
        return true;
    }

    public static void endPiece() {
        ArrayDeque<Piece> stack = PIECES.get();
        stack.pop();
        if (stack.isEmpty()) PIECES.remove();
    }

    public static Pending before(WorldGenRegion region, BlockPos pos,
                                 BlockState requested) {
        String origin = null;
        for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
            String name = frame.getClassName();
            if (name.startsWith("com.feel.gems.adventure.worldgen.Othman")
                    || name.startsWith("com.feel.gems.adventure.worldgen.Hushwire")) {
                origin = name.substring(name.lastIndexOf('.') + 1)
                        + "." + frame.getMethodName() + ":" + frame.getLineNumber();
                break;
            }
        }
        Piece piece = PIECES.get().peek();
        if (origin == null && piece == null) return Pending.SKIP;
        if (COUNT.incrementAndGet() > MAX_EVENTS) {
            throw new IllegalStateException("District writer trace exceeded " + MAX_EVENTS + " events");
        }
        return new Pending(origin == null ? "template" : "direct",
                origin == null ? piece.template() : origin,
                origin == null ? piece.target() : origin.startsWith("Hushwire")
                        ? "hushwire" : "othman_library",
                origin == null ? piece.box() : "-", pos.immutable(),
                state(region, pos), requested.toString());
    }

    public static void after(WorldGenRegion region, Pending pending, boolean accepted) {
        if (pending == Pending.SKIP) return;
        EVENTS.add(String.join("\t", pending.kind(), pending.origin(), pending.target(),
                pending.pieceBox(),
                Integer.toString(pending.pos().getX()), Integer.toString(pending.pos().getY()),
                Integer.toString(pending.pos().getZ()), pending.before(),
                pending.requested(), state(region, pending.pos()), Boolean.toString(accepted)));
    }

    private static String state(WorldGenRegion region, BlockPos pos) {
        if (!region.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return "<unavailable>";
        return region.getBlockState(pos).toString().replace('\t', ' ')
                .replace('\n', ' ');
    }

    private static Config readConfig() {
        Path input = Path.of(System.getProperty(PROPERTY)).toAbsolutePath().normalize();
        try {
            JsonObject root = JsonParser.parseString(Files.readString(input,
                    StandardCharsets.UTF_8)).getAsJsonObject();
            String seed = root.get("seed").getAsString();
            if (!seed.equals("SecondChanceDistrictFullA-20261003")
                    && !seed.equals("SecondChanceDistrictFullB-20261003")) {
                throw new IllegalStateException("Unexpected district writer trace seed");
            }
            JsonArray templates = root.getAsJsonArray("templates");
            List<String> ids = new ArrayList<>();
            for (JsonElement element : templates) ids.add(element.getAsString());
            if (!ids.equals(List.of(
                    "gems:hushwire_record_hall_v10", "gems:hushwire_receiver_field_v5",
                    "gems:hushwire_relay_crypt_v5", "gems:hushwire_procession_v5",
                    "gems:othman_library_archive_v11", "gems:othman_catalog_rotunda_v5",
                    "gems:othman_study_arrival_wing_v7"))) {
                throw new IllegalStateException("Trace must cover exactly seven staged templates");
            }
            return new Config(input, seed, List.copyOf(ids));
        } catch (IOException error) {
            throw new IllegalStateException("Cannot read district writer trace bounds: " + input, error);
        }
    }

    private static void flush() {
        Path output = CONFIG.input().resolveSibling("district-writer-trace.tsv");
        Path pending = output.resolveSibling(output.getFileName() + ".pending");
        Path receipt = output.resolveSibling("district-writer-trace.complete.json");
        Path pendingReceipt = receipt.resolveSibling(receipt.getFileName() + ".pending");
        try {
            if (Files.exists(output) || Files.exists(receipt)) {
                throw new IllegalStateException("Refusing to replace an existing district writer trace");
            }
            List<String> rows = new ArrayList<>(EVENTS);
            int events = COUNT.get();
            if (rows.size() != events) {
                throw new IllegalStateException("Writer trace dropped " + (events - rows.size()) + " events");
            }
            Collections.sort(rows);
            rows.addFirst("kind\torigin\ttarget\tpiece_box\tx\ty\tz\tbefore\trequested\tactual\taccepted");
            Files.write(pending, rows, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var stream = Files.newInputStream(pending)) {
                byte[] buffer = new byte[65536];
                int length;
                while ((length = stream.read(buffer)) != -1) {
                    digest.update(buffer, 0, length);
                }
            }
            JsonObject complete = new JsonObject();
            complete.addProperty("schema", 1);
            complete.addProperty("seed", CONFIG.seed());
            complete.addProperty("events", events);
            complete.addProperty("bytes", Files.size(pending));
            complete.addProperty("sha256", HexFormat.of().formatHex(digest.digest()));
            Files.writeString(pendingReceipt, complete + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            Files.move(pending, output, StandardCopyOption.ATOMIC_MOVE);
            Files.move(pendingReceipt, receipt, StandardCopyOption.ATOMIC_MOVE);
            System.out.println("DISTRICT_WRITER_TRACE seed=" + CONFIG.seed()
                    + " events=" + events + " file=" + output);
        } catch (Exception error) {
            System.err.println("DISTRICT_WRITER_TRACE_FAIL " + error);
        }
    }

    public record Pending(String kind, String origin, String target, String pieceBox, BlockPos pos,
                          String before, String requested) {
        public static final Pending SKIP = new Pending("skip", "skip", "skip", "-",
                BlockPos.ZERO, "", "");
    }

    private record Piece(String template, String target, String box) { }

    private record Config(Path input, String seed, List<String> templates) { }
}
