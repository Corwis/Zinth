package net.zanoria.zinth.evidence;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;

/**
 * Async writer for evidence dumps.
 * Never called from the main thread - IO stays off the tick loop.
 *
 * Output: evidence/{date}/{playerId}/{reason}_{timestamp}.txt
 * Path is relative to the server working directory.
 */
public final class EvidenceWriter {

    private static final DateTimeFormatter DATE_DIR  = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("HH-mm-ss").withZone(ZoneOffset.UTC);

    private final Path baseDir;
    private final ExecutorService executor;

    public EvidenceWriter() {
        this.baseDir = Path.of("evidence");
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "zinth-evidence-writer");
            t.setDaemon(true);
            return t;
        });
    }

    // -------------------------------------------------------------------------
    // API
    // -------------------------------------------------------------------------

    /** Enqueue a dump for async writing. Returns immediately - never blocks. */
    public void submit(EvidenceDump dump) {
        executor.execute(() -> writeDump(dump));
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // -------------------------------------------------------------------------
    // IO — runs on evidence writer thread only
    // -------------------------------------------------------------------------

    private void writeDump(EvidenceDump dump) {
        try {
            Instant wallClock = Instant.now();

            Path dir = baseDir
                .resolve(DATE_DIR.format(wallClock))
                .resolve(dump.playerId().toString());
            Files.createDirectories(dir);

            String fileName = dump.reason() + "_" + TIMESTAMP.format(wallClock) + ".txt";
            Path file = dir.resolve(fileName);

            try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {

                writer.write("=== ZINTH EVIDENCE DUMP ===");
                writer.newLine();
                writer.write("Player:   " + dump.playerId());
                writer.newLine();
                writer.write("Reason:   " + dump.reason());
                writer.newLine();
                writer.write("Meta:     " + dump.meta());
                writer.newLine();
                writer.write("Tick:     " + dump.dumpTick());
                writer.newLine();
                writer.write("Time:     " + wallClock);
                writer.newLine();
                writer.write("Entries:  " + dump.entryCount());
                writer.newLine();
                writer.write("===========================");
                writer.newLine();
                writer.newLine();

                for (EvidenceRingBuffer.Entry entry : dump.entries()) {
                    writer.write(formatEntry(entry));
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            // Log but never throw — writer must not crash the server
            System.err.println("[Zinth] Failed to write evidence dump for " + dump.playerId() + ": " + e.getMessage());
        }
    }

    private String formatEntry(EvidenceRingBuffer.Entry entry) {
        return switch (entry) {
            case EvidenceRingBuffer.SnapshotEntry s -> {
                var snap = s.snapshot();
                yield String.format("[SNAP] tick=%d x=%.3f y=%.3f z=%.3f yaw=%.2f pitch=%.2f dXZ=%.4f onGround=%b",
                    snap.tickCaptured(), snap.x(), snap.y(), snap.z(),
                    snap.yaw(), snap.pitch(), snap.deltaXZ(), snap.onGround());
            }
            case EvidenceRingBuffer.AttackEntry a ->
                String.format("[ATTACK] tick=%d attacker=%s target=%s nano=%d",
                    a.tick(), a.attacker(), a.target(), a.nanoTime());
            case EvidenceRingBuffer.VelocityEntry v ->
                String.format("[VEL] tick=%d player=%s vx=%.4f vy=%.4f vz=%.4f",
                    v.tick(), v.playerId(), v.velX(), v.velY(), v.velZ());
        };
    }
}
