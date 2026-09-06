package net.zanoria.zinth.packet.impl;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.protocol.game.*;
import net.zanoria.zinth.Zinth;
import net.zanoria.zinth.exploit.ExploitProtection;
import net.zanoria.zinth.exploit.PacketRateLimiter;
import net.zanoria.zinth.packet.events.*;

import java.util.UUID;

/**
 * Netty pipeline handler that intercepts incoming client packets
 * and publishes them to the PacketBus.
 *
 * Runs on the Netty IO thread — never blocks, never allocates unnecessarily.
 */
public final class ZinthChannelHandler extends ChannelDuplexHandler {

    public static final String HANDLER_NAME = "zinth_packet_interceptor";

    private final UUID playerId;

    /**
     * The per-type rate limits live here, not in ExploitGuard.
     *
     * <p>ExploitGuard sits before the decoder, where every message is a raw ByteBuf; its
     * {@code instanceof ServerboundMovePlayerPacket} could never match, so the 40/s and 30/s
     * limits applied to nothing at all. This handler sits after the decoder and already switches
     * on packet type — it is the first point in the pipeline where those limits can mean
     * anything.
     */
    private final PacketRateLimiter rateLimiter = new PacketRateLimiter();

    public ZinthChannelHandler(UUID playerId) {
        this.playerId = playerId;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!withinRateLimit(msg)) {
            // Decoded packets are not reference-counted the way frames are, but releasing is
            // still the correct thing to do for anything that is: ReferenceCountUtil is a no-op
            // otherwise, and getting this wrong before cost direct memory per dropped frame.
            io.netty.util.ReferenceCountUtil.release(msg);
            java.util.logging.Logger.getLogger("Zinth").warning(
                "[Zinth/ExploitGuard] closing connection of " + playerId
                    + ": packet rate above limit for " + msg.getClass().getSimpleName());
            ctx.channel().close();
            return;
        }
        try {
            handlePacket(msg);
        } finally {
            super.channelRead(ctx, msg);
        }
    }

    /** Per-type limits. Only reachable because this handler runs after the decoder. */
    private boolean withinRateLimit(Object msg) {
        if (msg instanceof ServerboundMovePlayerPacket) {
            return rateLimiter.allow("move", ExploitProtection.MAX_MOVE_PACKETS_PER_SECOND);
        }
        if (msg instanceof ServerboundInteractPacket) {
            return rateLimiter.allow("attack", ExploitProtection.MAX_ATTACK_PACKETS_PER_SECOND);
        }
        return true;
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
