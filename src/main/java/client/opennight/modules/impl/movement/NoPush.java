package client.opennight.modules.impl.movement;

import client.opennight.event.impl.SneakEvent;
import client.opennight.modules.Category;
import client.opennight.modules.Module;
import client.opennight.event.EventTarget;

public class NoPush
extends Module {
    public NoPush() {
        super("NoPush", Category.MOVEMENT);
    }

    @EventTarget
    public void onSneak(SneakEvent sneakEvent) {
        if (!FireballBlink.INSTANCE.isEnabled()) {
            sneakEvent.setCancelled(true);
        }
    }
}