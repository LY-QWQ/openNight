package client.opennight.modules.impl.movement;

import java.util.HashMap;

import client.opennight.event.impl.MotionEvent;
import net.minecraft.client.KeyMapping;
import client.opennight.event.impl.RotationEvent;
import client.opennight.modules.Category;
import client.opennight.modules.Module;
import client.opennight.modules.impl.player.InventoryManager;
import client.opennight.event.EventTarget;

public class Sprint
extends Module {
    // private final HashMap<String, String> keyMappings = new HashMap<>();
    public Sprint() {
        super("Sprint", Category.MOVEMENT);
        this.setEnabled(true);
    }

    @EventTarget
    public void onRotation(RotationEvent rotationEvent) {
        if (InventoryManager.isPerformingAction) {
            return;
        }
        mc.options.toggleSprint().set(false);
        KeyMapping.set(mc.options.keySprint.getKey(), true);
    }
}