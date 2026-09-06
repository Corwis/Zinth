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

        // ExploitGuard sits BEFORE the decoder, where only raw frames exist: size and frame
        // rate. Per-type limits belong to ZinthChannelHandler below — a type check here can
        // never match, which is exactly how the move and attack limits came to guard nothing.
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
