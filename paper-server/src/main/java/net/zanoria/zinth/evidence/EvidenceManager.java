package net.zanoria.zinth.evidence;

import net.zanoria.zinth.snapshot.PlayerSnapshot;
import org.bukkit.Bukkit;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages per-player EvidenceRingBuffers and coordinates dumps.
 * Main thread writes, async IO via EvidenceWriter.
 */
public final class EvidenceManager implements EvidenceService {

    private final ConcurrentHashMap<UUID, EvidenceRingBuffer> buffers = new ConcurrentHashMap<>();
    private EvidenceWriter writer;

    public void enable() {
        writer = new EvidenceWriter();
    }

    public void disable() {
        writer.shutdown();
    }

    public void tick() {
        // Reserved for future periodic sampling logic
    }

    public void onPlayerJoin(UUID playerId) {
        buffers.put(playerId, new EvidenceRingBuffer(playerId));
    }

    public void onPlayerQuit(UUID playerId) {
        buffers.remove(playerId);
    }

    private EvidenceRingBuffer buffer(UUID playerId) {
        return buffers.computeIfAbsent(playerId, EvidenceRingBuffer::new);
    }

    @Override
    public void captureSnapshot(UUID playerId, PlayerSnapshot snapshot) {
        buffer(playerId).captureSnapshot(snapshot);
    }

    @Override
    public void captureAttack(UUID attacker, UUID target, long tick) {
        buffer(attacker).captureAttack(attacker, target, tick);
        buffer(target).captureAttack(attacker, target, tick);
    }

    @Override
    public void captureVelocity(UUID playerId, double velX, double velY, double velZ, long tick) {
        buffer(playerId).captureVelocity(velX, velY, velZ, tick);
    }

    @Override
    public void dump(UUID playerId, String reason, String meta) {
        EvidenceRingBuffer buf = buffers.get(playerId);
        if (buf == null) return;

        EvidenceDump dump = new EvidenceDump(
            playerId,
            reason,
            meta,
            Bukkit.getCurrentTick(),
            System.nanoTime(),
            buf.drain()
        );
        writer.submit(dump);
    }
}
