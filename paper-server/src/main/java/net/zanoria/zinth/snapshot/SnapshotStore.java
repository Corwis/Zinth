package net.zanoria.zinth.snapshot;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe storage for player snapshots.
 * Write only from main thread, read from any thread.
 */
public final class SnapshotStore {

    private static final class Entry {
        volatile PlayerSnapshot current;
        volatile PlayerSnapshot last;

        Entry(PlayerSnapshot current) {
            this.current = current;
            this.last = null;
        }
    }

    private final ConcurrentHashMap<UUID, Entry> entries = new ConcurrentHashMap<>();

    public void update(PlayerSnapshot snapshot) {
        entries.compute(snapshot.playerId(), (id, entry) -> {
            if (entry == null) {
                return new Entry(snapshot);
            }
            entry.last = entry.current;
            entry.current = snapshot;
            return entry;
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

    public int size() {
        return entries.size();
    }

    public void clear() {
        entries.clear();
    }
}
