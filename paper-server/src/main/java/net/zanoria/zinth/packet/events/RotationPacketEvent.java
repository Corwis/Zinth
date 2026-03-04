package net.zanoria.zinth.packet.events;

import net.zanoria.zinth.packet.PacketEvent;
import java.util.UUID;

public record RotationPacketEvent(
    UUID playerId,
    float yaw, float pitch,
    long nanoTime
) implements PacketEvent {}
