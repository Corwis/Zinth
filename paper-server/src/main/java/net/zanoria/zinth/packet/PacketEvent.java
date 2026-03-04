package net.zanoria.zinth.packet;

import java.util.UUID;

/**
 * Base interface for all Zinth packet events.
 */
public interface PacketEvent {

    UUID playerId();
    long nanoTime();
}
