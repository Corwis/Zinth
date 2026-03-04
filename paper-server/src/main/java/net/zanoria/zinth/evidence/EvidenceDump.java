package net.zanoria.zinth.evidence;

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
    EvidenceRingBuffer.Entry[] entries
) {
    public int entryCount() {
        int count = 0;
        for (EvidenceRingBuffer.Entry e : entries) {
            if (e != null) count++;
        }
        return count;
    }
}
