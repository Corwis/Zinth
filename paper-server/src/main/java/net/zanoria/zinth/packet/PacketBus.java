package net.zanoria.zinth.packet;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Central packet event bus — the implementation behind {@link IPacketBus}.
 *
 * Publishing happens on the Netty thread — consumers must not block.
 * Events are queued and flushed on the main thread each tick.
 *
 * Threading:
 *   publish()     → Netty thread
 *   flushQueue()  → main thread (called from Zinth.tick())
 *   subscribe()   → any thread
 */
public final class PacketBus implements IPacketBus {

    private final ConcurrentHashMap<Class<?>, CopyOnWriteArrayList<Consumer<PacketEvent>>> subscribers
        = new ConcurrentHashMap<>();

    private final ConcurrentLinkedQueue<PacketEvent> queue = new ConcurrentLinkedQueue<>();

    // -------------------------------------------------------------------------
    // Subscribe
    // -------------------------------------------------------------------------

    @Override
    @SuppressWarnings("unchecked")
    public <T extends PacketEvent> void subscribe(Class<T> type, Consumer<T> consumer) {
        subscribers
            .computeIfAbsent(type, k -> new CopyOnWriteArrayList<>())
            .add((Consumer<PacketEvent>) (Consumer<?>) consumer);
    }

    @Override
    public void subscribeAll(Consumer<PacketEvent> consumer) {
        subscribe(PacketEvent.class, consumer);
    }

    // -------------------------------------------------------------------------
    // Publish — Netty thread
    // -------------------------------------------------------------------------

    @Override
    public void publish(PacketEvent event) {
        queue.add(event);
    }

    // -------------------------------------------------------------------------
    // Flush — main thread, called from Zinth.tick()
    // -------------------------------------------------------------------------

    public void flushQueue() {
        PacketEvent event;
        while ((event = queue.poll()) != null) {
            dispatch(event);
        }
    }

    private void dispatch(PacketEvent event) {
        // Dispatch to specific type subscribers
        CopyOnWriteArrayList<Consumer<PacketEvent>> specific = subscribers.get(event.getClass());
        if (specific != null) {
            for (Consumer<PacketEvent> consumer : specific) {
                deliver(consumer, event);
            }
        }

        // Dispatch to subscribeAll subscribers
        CopyOnWriteArrayList<Consumer<PacketEvent>> all = subscribers.get(PacketEvent.class);
        if (all != null) {
            for (Consumer<PacketEvent> consumer : all) {
                deliver(consumer, event);
            }
        }
    }

    /**
     * A subscriber lives in a plugin. If it throws, the exception would otherwise travel up
     * through Zinth.tick() into MinecraftServer.tickServer() and take the tick loop with it,
     * so a broken plugin could stop the server. Swallow it at the boundary instead.
     */
    private void deliver(Consumer<PacketEvent> consumer, PacketEvent event) {
        try {
            consumer.accept(event);
        } catch (Throwable t) {
            java.util.logging.Logger.getLogger("Zinth").log(
                java.util.logging.Level.WARNING,
                "[Zinth] PacketBus subscriber threw on " + event.getClass().getSimpleName(), t);
        }
    }

    /** Number of registered subscribers across all event types. Diagnostics only. */
    public int subscriberCount() {
        int total = 0;
        for (CopyOnWriteArrayList<Consumer<PacketEvent>> list : subscribers.values()) {
            total += list.size();
        }
        return total;
    }

    /** Events captured but not yet dispatched. Diagnostics only. */
    public int pendingCount() {
        return queue.size();
    }
}
