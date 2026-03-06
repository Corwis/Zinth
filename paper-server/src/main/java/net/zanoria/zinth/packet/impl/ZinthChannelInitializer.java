package net.zanoria.zinth.packet.impl;

import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;

import java.util.UUID;

/**
 * Injects ZinthChannelHandler into the Netty pipeline for a player.
 * Called from PlayerList when the connection is established.
 */
public final class ZinthChannelInitializer {

    private ZinthChannelInitializer() {}

    /**
     * Call this on the Netty thread when a player's channel is ready.
     * Inserts Zinth's handler before the packet decoder.
     */
    public static void inject(Channel channel, UUID playerId) {
        ChannelPipeline pipeline = channel.pipeline();
        if (pipeline.get(ZinthChannelHandler.HANDLER_NAME) != null) return;
        pipeline.addBefore("packet_handler", ZinthChannelHandler.HANDLER_NAME,
            new ZinthChannelHandler(playerId));
    }

    /**
     * Remove Zinth's handler from the pipeline on disconnect.
     */
    public static void uninject(Channel channel) {
        ChannelPipeline pipeline = channel.pipeline();
        if (pipeline.get(ZinthChannelHandler.HANDLER_NAME) != null) {
            pipeline.remove(ZinthChannelHandler.HANDLER_NAME);
        }
    }
}
