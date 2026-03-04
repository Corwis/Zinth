package net.zanoria.zinth.evidence;

import net.zanoria.zinth.snapshot.PlayerSnapshot;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

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
    private final Entry[] buffer;
    private final int capacity;
    private final AtomicInteger writeIndex = new AtomicInteger(0);

    public EvidenceRingBuffer(UUID playerId, int capacity) {
        this.playerId = playerId;
        this.capacity = capacity;
        this.buffer = new Entry[capacity];
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
        int idx = writeIndex.getAndIncrement() % capacity;
        buffer[idx] = entry;
    }

    // -------------------------------------------------------------------------
    // Read — safe from any thread (snapshot of current state)
    // -------------------------------------------------------------------------

    /**
     * Returns a snapshot of all non-null entries in insertion order.
     * Allocates - only call when building a dump.
     */
    public Entry[] drain() {
        Entry[] copy = new Entry[capacity];
        System.arraycopy(buffer, 0, copy, 0, capacity);
        return copy;
    }

    public UUID playerId() { return playerId; }
    public int capacity()  { return capacity; }
}
