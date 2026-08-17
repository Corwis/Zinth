package net.zanoria.zinth.packet;

import net.zanoria.zinth.packet.events.AttackPacketEvent;
import net.zanoria.zinth.packet.events.RotationPacketEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PacketBusTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-0000000000e5");

    @Test
    public void eventsAreHeldUntilTheFlush() {
        PacketBus bus = new PacketBus();
        List<PacketEvent> seen = new ArrayList<>();
        bus.subscribeAll(seen::add);

        bus.publish(new RotationPacketEvent(PLAYER, 90f, 12f, 1_000L));
        assertEquals(0, seen.size(), "publish must not dispatch on the Netty thread");
        assertEquals(1, bus.pendingCount());

        bus.flushQueue();
        assertEquals(1, seen.size());
        assertEquals(0, bus.pendingCount());
    }

    @Test
    public void typedSubscribersOnlySeeTheirOwnType() {
        PacketBus bus = new PacketBus();
        List<RotationPacketEvent> rotations = new ArrayList<>();
        bus.subscribe(RotationPacketEvent.class, rotations::add);

        bus.publish(new RotationPacketEvent(PLAYER, 90f, 12f, 1_000L));
        bus.publish(new AttackPacketEvent(PLAYER, 42, 1_001L));
        bus.flushQueue();

        assertEquals(1, rotations.size());
        assertEquals(90f, rotations.get(0).yaw());
    }

    @Test
    public void theNanoTimestampSurvivesTheQueue() {
        // The whole point of the bus: the tick-quantised Bukkit view cannot tell two rotations
        // in the same tick apart, and these timestamps can.
        PacketBus bus = new PacketBus();
        List<PacketEvent> seen = new ArrayList<>();
        bus.subscribeAll(seen::add);

        bus.publish(new RotationPacketEvent(PLAYER, 10f, 0f, 5_000L));
        bus.publish(new RotationPacketEvent(PLAYER, 20f, 0f, 5_400L));
        bus.flushQueue();

        assertEquals(5_000L, seen.get(0).nanoTime());
        assertEquals(5_400L, seen.get(1).nanoTime());
    }

    @Test
    public void aThrowingSubscriberDoesNotStopTheOthers() {
        // Subscribers live in plugins. Before the guard, one bad consumer took the exception up
        // through Zinth.tick() into MinecraftServer.tickServer().
        PacketBus bus = new PacketBus();
        List<PacketEvent> survivor = new ArrayList<>();
        bus.subscribe(RotationPacketEvent.class, e -> { throw new IllegalStateException("plugin bug"); });
        bus.subscribe(RotationPacketEvent.class, survivor::add);

        bus.publish(new RotationPacketEvent(PLAYER, 1f, 2f, 9L));
        bus.flushQueue(); // must not throw

        assertEquals(1, survivor.size(), "a broken subscriber swallowed the event for everyone else");
    }

    @Test
    public void theBusIsUsableThroughItsInterface() {
        // What a plugin gets from Bukkit.getServicesManager().load(IPacketBus.class).
        IPacketBus bus = new PacketBus();
        List<PacketEvent> seen = new ArrayList<>();
        bus.subscribeAll(seen::add);
        bus.publish(new AttackPacketEvent(PLAYER, 7, 3L));
        ((PacketBus) bus).flushQueue();

        assertEquals(1, seen.size());
        assertInstanceOf(AttackPacketEvent.class, seen.get(0));
    }

    @Test
    public void subscriberCountReflectsRegistrations() {
        PacketBus bus = new PacketBus();
        assertEquals(0, bus.subscriberCount());
        bus.subscribe(RotationPacketEvent.class, e -> { });
        bus.subscribeAll(e -> { });
        assertEquals(2, bus.subscriberCount());
    }

    @Test
    public void everyEventTypeCarriesThePlayerAndTheTimestamp() {
        // The six records are the API surface a reader compiles against; keep them honest.
        List<PacketEvent> all = List.of(
            new RotationPacketEvent(PLAYER, 1f, 2f, 11L),
            new AttackPacketEvent(PLAYER, 3, 12L),
            new net.zanoria.zinth.packet.events.MovePacketEvent(PLAYER, 1, 2, 3, true, 13L),
            new net.zanoria.zinth.packet.events.UseItemPacketEvent(PLAYER, 0, 14L),
            new net.zanoria.zinth.packet.events.BlockPlacePacketEvent(PLAYER, 1, 2, 3, 4, 15L),
            new net.zanoria.zinth.packet.events.SlotChangePacketEvent(PLAYER, 5, 16L));

        for (PacketEvent event : all) {
            assertEquals(PLAYER, event.playerId(), event.getClass().getSimpleName());
            assertTrue(event.nanoTime() > 0, event.getClass().getSimpleName());
        }
        assertEquals(6, all.size());
    }
}
