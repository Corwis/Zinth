package net.zanoria.zinth.packet.events;

import net.zanoria.zinth.packet.PacketEvent;
import java.util.UUID;

public record MovePacketEvent(
    UUID playerId,
    double x, double y, double z,
    boolean onGround,
    long nanoTime
) implements PacketEvent {}
