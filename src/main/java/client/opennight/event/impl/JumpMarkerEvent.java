package client.opennight.event.impl;

import lombok.AllArgsConstructor;
import lombok.Data;
import client.opennight.event.EventMarker;

@Data
@AllArgsConstructor
public class JumpMarkerEvent
implements EventMarker {
    private float yaw;
}