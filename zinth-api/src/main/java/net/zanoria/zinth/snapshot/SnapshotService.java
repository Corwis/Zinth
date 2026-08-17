package net.zanoria.zinth.snapshot;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Public read-only API for snapshot access.
 * Lightlay and plugins use ONLY this interface - never the store directly.
 */
public interface SnapshotService {

    @Nullable PlayerSnapshot getCurrent(UUID playerId);

    @Nullable PlayerSnapshot getLast(UUID playerId);

    /**
     * Returns up to {@code maxCount} snapshots ordered oldest→newest.
     * Ring buffer holds 100 entries at 100ms resolution (10 seconds of history).
     */
    @NotNull List<PlayerSnapshot> getHistory(UUID playerId, int maxCount);

    int trackedCount();
}
