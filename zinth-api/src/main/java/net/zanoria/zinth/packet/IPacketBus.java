package net.zanoria.zinth.packet;

import java.util.function.Consumer;

/**
 * Read side of Zinth's packet capture layer.
 *
 * <p>Events are captured on the Netty IO thread and queued; subscribers are called on the
 * main thread once per tick, so a consumer may touch the Bukkit API but must stay short.
 * Each event carries the {@link System#nanoTime()} at which the packet was decoded — that
 * timestamp is the reason this bus exists, because it survives the tick quantisation that
 * every Bukkit-level observation is subject to.
 *
 * <p>Reachable from a plugin as
 * {@code Bukkit.getServicesManager().load(IPacketBus.class)}. The concrete implementation
 * is registered under {@code PacketBus.class} as well, for readers that predate this
 * interface.
 */
public interface IPacketBus {

    /** Subscribes to one event type. See {@code net.zanoria.zinth.packet.events}. */
    <T extends PacketEvent> void subscribe(Class<T> type, Consumer<T> consumer);

    /** Subscribes to every event type. */
    void subscribeAll(Consumer<PacketEvent> consumer);

    /** Publishes an event. Called by Zinth itself from the Netty thread. */
    void publish(PacketEvent event);
}
