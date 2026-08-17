package net.zanoria.zinth.evidence;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The evidence ring buffer. Both defects here were latent rather than visible: the buffer had
 * no producer, so neither had ever run.
 */
public class EvidenceRingBufferTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-0000000000c3");
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-0000000000d4");

    @Test
    public void entriesComeBackInInsertionOrder() {
        EvidenceRingBuffer buffer = new EvidenceRingBuffer(PLAYER, 8);
        for (int i = 0; i < 5; i++) {
            buffer.captureAttack(PLAYER, TARGET, i);
        }

        List<EvidenceRingBuffer.Entry> entries = buffer.drain();
        assertEquals(5, entries.size());
        for (int i = 0; i < 5; i++) {
            assertEquals(i, ((EvidenceRingBuffer.AttackEntry) entries.get(i)).tick(),
                "entry " + i + " is out of order");
        }
    }

    @Test
    public void wrappingKeepsTheNewestEntriesOldestFirst() {
        EvidenceRingBuffer buffer = new EvidenceRingBuffer(PLAYER, 4);
        for (int i = 0; i < 10; i++) {
            buffer.captureAttack(PLAYER, TARGET, i);
        }

        List<EvidenceRingBuffer.Entry> entries = buffer.drain();
        assertEquals(4, entries.size());
        assertEquals(6, ((EvidenceRingBuffer.AttackEntry) entries.get(0)).tick());
        assertEquals(9, ((EvidenceRingBuffer.AttackEntry) entries.get(3)).tick());
    }

    @Test
    public void anEmptyBufferDrainsToNothing() {
        assertEquals(List.of(), new EvidenceRingBuffer(PLAYER, 4).drain());
        assertEquals(0, new EvidenceRingBuffer(PLAYER, 4).size());
    }

    @Test
    public void theWriteIndexSurvivesPastIntegerMaxValue() throws Exception {
        // The old implementation used an int counter and `getAndIncrement() % capacity`. Past
        // Integer.MAX_VALUE that index is negative and the write throws inside the tick loop.
        // Drive the real buffer across the wrap point instead of asserting the arithmetic:
        // reflection sets the counter just below the old overflow, then writes over it.
        EvidenceRingBuffer buffer = new EvidenceRingBuffer(PLAYER, 4);
        var counter = EvidenceRingBuffer.class.getDeclaredField("writeCount");
        counter.setAccessible(true);
        ((java.util.concurrent.atomic.AtomicLong) counter.get(buffer)).set(Integer.MAX_VALUE - 1L);

        for (int i = 0; i < 5; i++) {
            buffer.captureAttack(PLAYER, TARGET, i); // would throw with an int counter
        }

        List<EvidenceRingBuffer.Entry> entries = buffer.drain();
        assertEquals(4, entries.size());
        assertEquals(4, ((EvidenceRingBuffer.AttackEntry) entries.get(3)).tick(),
            "the newest write was lost across the wrap");
    }

    @Test
    public void aDumpCountsWhatIsActuallyThere() {
        EvidenceRingBuffer buffer = new EvidenceRingBuffer(PLAYER, 4);
        buffer.captureVelocity(0.1, 0.4, -0.2, 12L);
        buffer.captureAttack(PLAYER, TARGET, 12L);

        EvidenceDump dump = new EvidenceDump(PLAYER, "test", null, 12L, 0L, buffer.drain());
        assertEquals(2, dump.entryCount());
    }
}
