package net.zanoria.zinth.packet.events;

import net.zanoria.zinth.packet.PacketEvent;
import java.util.UUID;

public record AttackPacketEvent(
    UUID playerId,
    int targetEntityId,
    long nanoTime
) implements PacketEvent {}
