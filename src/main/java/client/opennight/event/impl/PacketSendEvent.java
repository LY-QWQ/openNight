package client.opennight.event.impl;

import lombok.Generated;
import net.minecraft.network.protocol.Packet;
import client.opennight.event.Event;

public class PacketSendEvent
extends Event {
    private final Packet<?> packet;

    @Generated
    public PacketSendEvent(Packet<?> packet) {
        this.packet = packet;
    }
}