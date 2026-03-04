package net.zanoria.zinth.snapshot;

import org.jetbrains.annotations.Nullable;
import java.util.UUID;

/**
 * Public read-only API for snapshot access.
 * Lightlay and plugins use ONLY this interface - never the store directly.
 */
public interface SnapshotService {

    @Nullable PlayerSnapshot getCurrent(UUID playerId);

    @Nullable PlayerSnapshot getLast(UUID playerId);

    int trackedCount();
}
