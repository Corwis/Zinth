package net.zanoria.zinth.shadow;

import org.jetbrains.annotations.Nullable;
import java.util.UUID;

/**
 * Public API for shadow state.
 * Velocity reads this to route players into shadow queues.
 */
public interface ShadowService {

    void setShadow(UUID playerId, boolean shadow, String reason);

    boolean isShadow(UUID playerId);

    @Nullable String shadowReason(UUID playerId);

    long shadowSince(UUID playerId);
}
