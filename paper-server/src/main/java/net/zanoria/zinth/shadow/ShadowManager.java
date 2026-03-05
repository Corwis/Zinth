package net.zanoria.zinth.shadow;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages shadow state per player.
 * Thread-safe — readable from any thread.
 */
public final class ShadowManager implements ShadowService {

    private final ConcurrentHashMap<UUID, ShadowState> states = new ConcurrentHashMap<>();

    public void onPlayerQuit(UUID playerId) {
        states.remove(playerId);
    }

    @Override
    public void setShadow(UUID playerId, boolean shadow, String reason) {
        if (shadow) {
            states.put(playerId, new ShadowState(
                playerId,
                true,
                reason,
                System.currentTimeMillis()
            ));
        } else {
            states.remove(playerId);
        }
    }

    @Override
    public boolean isShadow(UUID playerId) {
        ShadowState state = states.get(playerId);
        return state != null && state.shadow();
    }

    @Override
    public String shadowReason(UUID playerId) {
        ShadowState state = states.get(playerId);
        return state != null ? state.reason() : null;
    }

    @Override
    public long shadowSince(UUID playerId) {
        ShadowState state = states.get(playerId);
        return state != null ? state.since() : -1L;
    }
}
