package net.zanoria.zinth.packet;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Central packet event bus.
 *
 * Publishing happens on the Netty thread — consumers must not block.
 * Events are queued and flushed on the main thread each tick.
 *
 * Threading:
 *   publish()     → Netty thread
 *   flushQueue()  → main thread (called from Zinth.tick())
 *   subscribe()   → any thread (before server starts)
 */
public final class PacketBus {

    private final ConcurrentHashMap<Class<?>, CopyOnWriteArrayList<Consumer<PacketEvent>>> subscribers
        = new ConcurrentHashMap<>();

    private final java.util.concurrent.ConcurrentLinkedQueue<PacketEvent> queue
        = new java.util.concurrent.ConcurrentLinkedQueue<>();

    // -------------------------------------------------------------------------
    // Subscribe
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    public <T extends PacketEvent> void subscribe(Class<T> type, Consumer<T> consumer) {
        subscribers
            .computeIfAbsent(type, k -> new CopyOnWriteArrayList<>())
            .add((Consumer<PacketEvent>) (Consumer<?>) consumer);
    }

    public void subscribeAll(Consumer<PacketEvent> consumer) {
        subscribe(PacketEvent.class, consumer);
    }

    // -------------------------------------------------------------------------
    // Publish — Netty thread
    // -------------------------------------------------------------------------

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
                consumer.accept(event);
            }
        }

        // Dispatch to subscribeAll subscribers
        CopyOnWriteArrayList<Consumer<PacketEvent>> all = subscribers.get(PacketEvent.class);
        if (all != null) {
            for (Consumer<PacketEvent> consumer : all) {
                consumer.accept(event);
            }
        }
    }
}
