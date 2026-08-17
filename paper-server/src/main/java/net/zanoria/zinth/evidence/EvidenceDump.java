package net.zanoria.zinth.evidence;

import java.util.List;
import java.util.UUID;

/**
 * Immutable snapshot of a player's evidence buffer at dump time.
 * Handed off to EvidenceWriter for async IO.
 */
public record EvidenceDump(
    UUID playerId,
    String reason,
    String meta,
    long dumpTick,
    long dumpNanoTime,
    List<EvidenceRingBuffer.Entry> entries
) {
    public int entryCount() {
        return entries.size();
    }
}
