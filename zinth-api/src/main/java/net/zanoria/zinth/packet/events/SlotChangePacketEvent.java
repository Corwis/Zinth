package net.zanoria.zinth.packet.events;

import net.zanoria.zinth.packet.PacketEvent;
import java.util.UUID;

public record SlotChangePacketEvent(
    UUID playerId,
    int slot,
    long nanoTime
) implements PacketEvent {}
