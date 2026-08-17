package net.zanoria.zinth.evidence;

import net.zanoria.zinth.snapshot.PlayerSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Fixed-size ring buffer storing recent evidence entries for one player.
 * Lock-free writes from main thread, safe reads from any thread.
 *
 * Capacity: configurable, default 400 entries (~20s at 20 TPS).
 */
public final class EvidenceRingBuffer {

    public sealed interface Entry permits
        EvidenceRingBuffer.SnapshotEntry,
        EvidenceRingBuffer.AttackEntry,
        EvidenceRingBuffer.VelocityEntry {}

    public record SnapshotEntry(PlayerSnapshot snapshot) implements Entry {}
    public record AttackEntry(UUID attacker, UUID target, long tick, long nanoTime) implements Entry {}
    public record VelocityEntry(UUID playerId, double velX, double velY, double velZ, long tick) implements Entry {}

    // -------------------------------------------------------------------------

    private final UUID playerId;
    private final AtomicReferenceArray<Entry> buffer;
    private final int capacity;

    /**
     * Total writes ever, not an index. A {@code long} because an {@code int} counter wraps
     * negative after ~2.1 billion writes and {@code negative % capacity} is negative — which
     * would have turned into an ArrayIndexOutOfBoundsException inside the tick loop on a
     * long-lived server.
     */
    private final AtomicLong writeCount = new AtomicLong(0);

    public EvidenceRingBuffer(UUID playerId, int capacity) {
        this.playerId = playerId;
        this.capacity = capacity;
        this.buffer = new AtomicReferenceArray<>(capacity);
    }

    public EvidenceRingBuffer(UUID playerId) {
        this(playerId, 400);
    }

    // -------------------------------------------------------------------------
    // Write — main thread only
    // -------------------------------------------------------------------------

    public void captureSnapshot(PlayerSnapshot snapshot) {
        write(new SnapshotEntry(snapshot));
    }

    public void captureAttack(UUID attacker, UUID target, long tick) {
        write(new AttackEntry(attacker, target, tick, System.nanoTime()));
    }

    public void captureVelocity(double velX, double velY, double velZ, long tick) {
        write(new VelocityEntry(playerId, velX, velY, velZ, tick));
    }

    private void write(Entry entry) {
        long seq = writeCount.getAndIncrement();
        buffer.set((int) Math.floorMod(seq, capacity), entry);
    }

    // -------------------------------------------------------------------------
    // Read — safe from any thread (snapshot of current state)
    // -------------------------------------------------------------------------

    /**
     * Returns the retained entries in insertion order, oldest first.
     * Allocates — only call when building a dump.
     */
    public List<Entry> drain() {
        long written = writeCount.get();
        int size = (int) Math.min(written, capacity);
        List<Entry> out = new ArrayList<>(size);
        // Oldest retained write is (written - size); walk forward from there.
        for (long seq = written - size; seq < written; seq++) {
            Entry e = buffer.get((int) Math.floorMod(seq, capacity));
            if (e != null) out.add(e);
        }
        return out;
    }

    public UUID playerId() { return playerId; }
    public int capacity()  { return capacity; }

    /** Entries currently retained. */
    public int size() { return (int) Math.min(writeCount.get(), capacity); }
}
