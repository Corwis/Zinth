package net.zanoria.zinth.packet.impl;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.protocol.game.*;
import net.zanoria.zinth.Zinth;
import net.zanoria.zinth.packet.events.*;

import java.util.UUID;

/**
 * Netty pipeline handler that intercepts incoming client packets
 * and publishes them to the PacketBus.
 *
 * Runs on the Netty IO thread — never blocks, never allocates unnecessarily.
 */
@io.netty.channel.ChannelHandler.Sharable
public final class ZinthChannelHandler extends ChannelDuplexHandler {

    public static final String HANDLER_NAME = "zinth_packet_interceptor";

    private final UUID playerId;

    public ZinthChannelHandler(UUID playerId) {
        this.playerId = playerId;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        try {
            handlePacket(msg);
        } finally {
            super.channelRead(ctx, msg);
        }
    }

    private void handlePacket(Object msg) {
        long nano = System.nanoTime();

        if (msg instanceof ServerboundMovePlayerPacket.PosRot p) {
            Zinth.get().packetBus().publish(new MovePacketEvent(
                playerId, p.getX(0), p.getY(0), p.getZ(0), p.isOnGround(), nano));
            Zinth.get().packetBus().publish(new RotationPacketEvent(
                playerId, p.getYRot(0), p.getXRot(0), nano));

        } else if (msg instanceof ServerboundMovePlayerPacket.Pos p) {
            Zinth.get().packetBus().publish(new MovePacketEvent(
                playerId, p.getX(0), p.getY(0), p.getZ(0), p.isOnGround(), nano));

        } else if (msg instanceof ServerboundMovePlayerPacket.Rot p) {
            Zinth.get().packetBus().publish(new RotationPacketEvent(
                playerId, p.getYRot(0), p.getXRot(0), nano));

        } else if (msg instanceof ServerboundInteractPacket p) {
            if (p.isAttack()) {
                Zinth.get().packetBus().publish(new AttackPacketEvent(
                    playerId, p.getEntityId(), nano));
            }

        } else if (msg instanceof ServerboundUseItemPacket p) {
            Zinth.get().packetBus().publish(new UseItemPacketEvent(
                playerId, p.getHand().ordinal(), nano));

        } else if (msg instanceof ServerboundUseItemOnPacket p) {
            Zinth.get().packetBus().publish(new BlockPlacePacketEvent(
                playerId,
                p.getHitResult().getBlockPos().getX(),
                p.getHitResult().getBlockPos().getY(),
                p.getHitResult().getBlockPos().getZ(),
                p.getHitResult().getDirection().ordinal(),
                nano));

        } else if (msg instanceof ServerboundSetCarriedItemPacket p) {
            Zinth.get().packetBus().publish(new SlotChangePacketEvent(
                playerId, p.getSlot(), nano));
        }
    }
}
