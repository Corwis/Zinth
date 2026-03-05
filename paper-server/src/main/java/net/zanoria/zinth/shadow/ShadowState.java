package net.zanoria.zinth.shadow;

import java.util.UUID;

/**
 * Immutable shadow state for a single player.
 */
public record ShadowState(
    UUID playerId,
    boolean shadow,
    String reason,
    long since
) {}
