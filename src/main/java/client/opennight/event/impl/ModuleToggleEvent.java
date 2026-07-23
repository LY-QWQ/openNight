package client.opennight.event.impl;

import client.opennight.event.EventMarker;
import client.opennight.modules.Module;

public record ModuleToggleEvent(Module module, boolean enabled) implements EventMarker {
}
