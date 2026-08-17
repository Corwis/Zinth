package net.zanoria.zinth.packet.events;

import net.zanoria.zinth.packet.PacketEvent;
import java.util.UUID;

public record BlockPlacePacketEvent(
    UUID playerId,
    int blockX, int blockY, int blockZ,
    int face,
    long nanoTime
) implements PacketEvent {}
