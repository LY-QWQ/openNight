package client.opennight.event.impl;

import lombok.*;
import client.opennight.event.EventMarker;

@AllArgsConstructor
@Data
public class UseItemRayTraceEvent
implements EventMarker {
    @Getter @Setter
    private float yaw;
    @Getter @Setter
    private float pitch;
}