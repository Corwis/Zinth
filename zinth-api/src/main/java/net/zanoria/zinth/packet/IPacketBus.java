package net.zanoria.zinth.packet;

import java.util.function.Consumer;

public interface IPacketBus {
    <T extends PacketEvent> void subscribe(Class<T> type, Consumer<T> consumer);
    void subscribeAll(Consumer<PacketEvent> consumer);
    void publish(PacketEvent event);
}