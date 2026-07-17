package client.nilore.modules.impl.movement;

import java.util.HashMap;

import client.nilore.event.impl.MotionEvent;
import net.minecraft.client.KeyMapping;
import client.nilore.event.impl.RotationEvent;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.modules.impl.player.InventoryManager;
import client.nilore.event.EventTarget;

public class Sprint
extends Module {
    // private final HashMap<String, String> keyMappings = new HashMap<>();
    public Sprint() {
        super("Sprint", Category.MOVEMENT);
        this.setEnabled(true);
    }

    @EventTarget
    public void onMotion(MotionEvent e) {
        if (e.pre) {
            if (InventoryManager.INSTANCE != null && InventoryManager.INSTANCE.isSuppressingSprint()) {
                mc.options.keySprint.setDown(false);
                if (mc.player != null) {
                    mc.player.setSprinting(false);
                }
                return;
            }

            mc.options.keySprint.setDown(true);
            mc.options.toggleSprint().set(false);
        }
    }
    @Override
    public void onDisable() {
        mc.options.keySprint.setDown(false);
    }
}