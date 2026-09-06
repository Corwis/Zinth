package net.zanoria.zinth.packet.impl;

import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import net.zanoria.zinth.exploit.ExploitGuard;
import net.zanoria.zinth.exploit.ExploitProtection;

import java.util.UUID;

/**
 * Injects Zinth handlers into the Netty pipeline for a player.
 * Order: ExploitGuard (before decoder) → ZinthChannelHandler (after decoder)
 */
public final class ZinthChannelInitializer {

    private ZinthChannelInitializer() {}

    public static void inject(Channel channel, UUID playerId) {
        ChannelPipeline pipeline = channel.pipeline();

        // ⚠️ ExploitGuard MUST stay before the decoder. Do not "fix" it by moving it.
        //
        // The tempting move is real: this handler used to check `msg instanceof
        // ServerboundMovePlayerPacket`, which cannot match here, so the obvious repair looks
        // like addBefore("packet_handler", ...). That trade is a loss. The decoder is where the
        // expensive work happens — allocating and parsing the frame, NBT included — and a guard
        // that runs after it has already paid for the attack it was built to refuse. Rejecting a
        // 2 MiB frame here costs a length comparison; rejecting it after the decoder costs the
        // decode. Decode-DoS is the whole reason this handler exists.
        //
        // So the question was split rather than moved: size and frame rate stay here, where a
        // raw frame can answer them. The per-type limits (40/s move, 30/s attack) live in
        // ZinthChannelHandler below, which runs after the decoder and can see a type. A type
        // check in THIS handler is always dead code — ZinthWiringTest fails if one reappears.
        if (pipeline.get(ExploitProtection.HANDLER_NAME) == null) {
            pipeline.addBefore("decoder", ExploitProtection.HANDLER_NAME,
                new ExploitGuard(playerId));
        }

        // ZinthChannelHandler sits AFTER the decoder (decoded packets)
        if (pipeline.get(ZinthChannelHandler.HANDLER_NAME) == null) {
            pipeline.addBefore("packet_handler", ZinthChannelHandler.HANDLER_NAME,
                new ZinthChannelHandler(playerId));
        }
    }

    public static void uninject(Channel channel) {
        ChannelPipeline pipeline = channel.pipeline();

        if (pipeline.get(ExploitProtection.HANDLER_NAME) != null) {
            pipeline.remove(ExploitProtection.HANDLER_NAME);
        }

        if (pipeline.get(ZinthChannelHandler.HANDLER_NAME) != null) {
            pipeline.remove(ZinthChannelHandler.HANDLER_NAME);
        }
    }
}
