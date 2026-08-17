package net.zanoria.zinth.snapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Thread-safe storage for player snapshots.
 * Write only from main thread, read from any thread.
 *
 * Ring buffer holds RING_SIZE entries at 100ms resolution = 10 seconds of history.
 */
public final class SnapshotStore {

    static final int RING_SIZE = 100;

    private static final class Entry {
        volatile PlayerSnapshot current;
        volatile PlayerSnapshot last;

        // Lock-free ring buffer: writes on main thread, reads from any thread.
        // AtomicReferenceArray ensures visibility without locking.
        final AtomicReferenceArray<PlayerSnapshot> ring = new AtomicReferenceArray<>(RING_SIZE);
        volatile int head  = 0; // next write slot (mod RING_SIZE)
        volatile int count = 0; // number of valid entries (capped at RING_SIZE)

        void pushCurrent(PlayerSnapshot snapshot) {
            last    = current;
            current = snapshot;
        }

        void push(PlayerSnapshot snapshot) {
            last    = current;
            current = snapshot;

            int slot = head;
            ring.set(slot, snapshot);
            head  = (slot + 1) % RING_SIZE;
            int c = count;
            if (c < RING_SIZE) count = c + 1;
        }

        List<PlayerSnapshot> getHistory(int n) {
            int c = count;
            int h = head;
            n = Math.min(n, c);
            if (n == 0) return Collections.emptyList();

            List<PlayerSnapshot> result = new ArrayList<>(n);
            for (int i = n - 1; i >= 0; i--) {
                int idx = ((h - 1 - i) % RING_SIZE + RING_SIZE) % RING_SIZE;
                PlayerSnapshot s = ring.get(idx);
                if (s != null) result.add(s);
            }
            return result;
        }
    }

    private final ConcurrentHashMap<UUID, Entry> entries = new ConcurrentHashMap<>();

    /** Full update: refreshes current/last and pushes to ring buffer. */
    public void update(PlayerSnapshot snapshot) {
        entries.compute(snapshot.playerId(), (id, entry) -> {
            Entry e = entry == null ? new Entry() : entry;
            e.push(snapshot);
            return e;
        });
    }

    /** Lightweight update: refreshes current/last only, no ring push. */
    public void updateCurrentOnly(PlayerSnapshot snapshot) {
        entries.compute(snapshot.playerId(), (id, entry) -> {
            Entry e = entry == null ? new Entry() : entry;
            e.pushCurrent(snapshot);
            return e;
        });
    }

    public void remove(UUID playerId) {
        entries.remove(playerId);
    }

    public PlayerSnapshot getCurrent(UUID playerId) {
        Entry e = entries.get(playerId);
        return e == null ? null : e.current;
    }

    public PlayerSnapshot getLast(UUID playerId) {
        Entry e = entries.get(playerId);
        return e == null ? null : e.last;
    }

    public List<PlayerSnapshot> getHistory(UUID playerId, int maxCount) {
        Entry e = entries.get(playerId);
        return e == null ? Collections.emptyList() : e.getHistory(maxCount);
    }

    public int size() {
        return entries.size();
    }

    public void clear() {
        entries.clear();
    }
}
