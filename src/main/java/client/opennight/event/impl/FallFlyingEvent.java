package client.opennight.event.impl;

import lombok.*;
import client.opennight.event.EventMarker;

@Data
@AllArgsConstructor
public class FallFlyingEvent
implements EventMarker {
    @Getter @Setter
    private float pitch;
}