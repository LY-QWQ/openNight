package client.opennight.event.impl;

import lombok.Getter;
import lombok.Generated;
import net.minecraft.network.protocol.Packet;
import client.opennight.event.Event;

public class PrePacketEvent
extends Event {
    @Getter
    private final Packet<?> packet;

    @Generated
    public PrePacketEvent(Packet<?> packet) {
        this.packet = packet;
    }
}