package net.zanoria.zinth.snapshot;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ten-second history ring. This is the only Zinth data that reaches a consumer today, and
 * {@code getHistory} is what Spectra compiles against.
 */
public class SnapshotStoreTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-0000000000f6");

    private static PlayerSnapshot at(double x, long tick) {
        return PlayerSnapshot.of(
            PLAYER, 1,
            x, 64.0, 0.0,
            x - 1, 64.0, 0.0,
            0f, 0f, 0f, 0f,
            0.0, 0.0, 0.0,
            true, false, false, false, false,
            20, 0f,
            tick, "minecraft:overworld");
    }

    @Test
    public void currentAndLastTrackTheTwoMostRecentPushes() {
        SnapshotStore store = new SnapshotStore();
        store.update(at(1, 1));
        assertEquals(1.0, store.getCurrent(PLAYER).x());
        assertNull(store.getLast(PLAYER), "there is no previous snapshot after the first push");

        store.update(at(2, 2));
        assertEquals(2.0, store.getCurrent(PLAYER).x());
        assertEquals(1.0, store.getLast(PLAYER).x());
    }

    @Test
    public void updateCurrentOnlyDoesNotGrowTheHistory() {
        SnapshotStore store = new SnapshotStore();
        store.update(at(1, 1));
        store.updateCurrentOnly(at(2, 2));
        store.updateCurrentOnly(at(3, 3));

        assertEquals(3.0, store.getCurrent(PLAYER).x(), "current must follow every capture");
        assertEquals(1, store.getHistory(PLAYER, 100).size(), "only pushes belong in the ring");
    }

    @Test
    public void historyComesBackOldestFirst() {
        SnapshotStore store = new SnapshotStore();
        for (int i = 1; i <= 5; i++) store.update(at(i, i));

        List<PlayerSnapshot> history = store.getHistory(PLAYER, 100);
        assertEquals(5, history.size());
        assertEquals(1.0, history.get(0).x());
        assertEquals(5.0, history.get(4).x());
    }

    @Test
    public void historyIsCappedAtTheRingSizeAndKeepsTheNewest() {
        SnapshotStore store = new SnapshotStore();
        int total = SnapshotStore.RING_SIZE + 25;
        for (int i = 1; i <= total; i++) store.update(at(i, i));

        List<PlayerSnapshot> history = store.getHistory(PLAYER, 1_000);
        assertEquals(SnapshotStore.RING_SIZE, history.size());
        assertEquals(total, history.get(history.size() - 1).x());
        assertEquals(total - SnapshotStore.RING_SIZE + 1, history.get(0).x());
    }

    @Test
    public void aBoundedRequestReturnsTheMostRecentEntries() {
        SnapshotStore store = new SnapshotStore();
        for (int i = 1; i <= 20; i++) store.update(at(i, i));

        List<PlayerSnapshot> history = store.getHistory(PLAYER, 3);
        assertEquals(3, history.size());
        assertEquals(18.0, history.get(0).x());
        assertEquals(20.0, history.get(2).x());
    }

    @Test
    public void anUnknownPlayerHasNoHistoryAndNoSnapshot() {
        SnapshotStore store = new SnapshotStore();
        UUID stranger = UUID.randomUUID();
        assertNull(store.getCurrent(stranger));
        assertNull(store.getLast(stranger));
        assertEquals(List.of(), store.getHistory(stranger, 10));
    }

    @Test
    public void removeAndClearDropEverything() {
        SnapshotStore store = new SnapshotStore();
        store.update(at(1, 1));
        assertEquals(1, store.size());

        store.remove(PLAYER);
        assertEquals(0, store.size());
        assertEquals(List.of(), store.getHistory(PLAYER, 10));

        store.update(at(2, 2));
        store.clear();
        assertEquals(0, store.size());
    }

    @Test
    public void derivedDeltasAreComputedFromThePreviousPosition() {
        PlayerSnapshot snapshot = at(5, 1); // lastX is 4
        assertEquals(1.0, snapshot.deltaX());
        assertEquals(1.0, snapshot.deltaXZ(), 1e-9);
        assertEquals(0, snapshot.chunkX());
    }

    @Test
    public void tenSecondsOfHistorySurvivesTheDocumentedTickCadence() {
        // SnapshotManager captures every tick and pushes every second tick, so 10 s of play is
        // 200 captures and must leave 100 ring entries spanning 200 ticks. Spectra's own replay
        // buffer only starts after a player becomes suspicious; this is the window before that.
        SnapshotStore store = new SnapshotStore();
        for (int tick = 1; tick <= 200; tick++) {
            if ((tick & 1) == 0) store.update(at(tick, tick));
            else store.updateCurrentOnly(at(tick, tick));
        }

        List<PlayerSnapshot> history = store.getHistory(PLAYER, 1_000);
        assertEquals(SnapshotStore.RING_SIZE, history.size());
        long span = history.get(history.size() - 1).tickCaptured() - history.get(0).tickCaptured();
        assertEquals(198, span, "the ring no longer spans ten seconds of ticks");
        assertNotNull(history.get(0).worldId());
    }
}
