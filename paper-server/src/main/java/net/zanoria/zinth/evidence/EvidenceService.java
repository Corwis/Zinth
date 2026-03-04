package net.zanoria.zinth.evidence;

import net.zanoria.zinth.snapshot.PlayerSnapshot;
import java.util.UUID;

/**
 * Public API for the evidence system.
 * Lightlay uses only this interface.
 */
public interface EvidenceService {

    void captureSnapshot(UUID playerId, PlayerSnapshot snapshot);

    void captureAttack(UUID attacker, UUID target, long tick);

    void captureVelocity(UUID playerId, double velX, double velY, double velZ, long tick);

    /**
     * Triggers an async evidence dump for the given player.
     * @param reason why the dump was triggered (e.g. "killaura_flag")
     * @param meta   optional extra info (e.g. JSON string)
     */
    void dump(UUID playerId, String reason, String meta);
}
