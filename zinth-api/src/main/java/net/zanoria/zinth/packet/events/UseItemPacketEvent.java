package net.zanoria.zinth.packet.events;

import net.zanoria.zinth.packet.PacketEvent;
import java.util.UUID;

public record UseItemPacketEvent(
    UUID playerId,
    int hand,
    long nanoTime
) implements PacketEvent {}
