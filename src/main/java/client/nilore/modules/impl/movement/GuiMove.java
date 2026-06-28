package client.nilore.modules.impl.movement;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.world.inventory.InventoryMenu;
import client.nilore.event.impl.StrafeEvent;
import client.nilore.gui.NewClickGui;
import client.nilore.gui.OldClickGui;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.modules.impl.player.InventoryManager;
import client.nilore.event.EventTarget;

public class GuiMove
extends Module {
    public static GuiMove INSTANCE;
    public GuiMove() {
        super("GuiMove", Category.MOVEMENT);
        INSTANCE = this;
    }

    @EventTarget
    public void onStrafe(StrafeEvent strafeEvent) {
        if (mc.player == null || mc.screen == null || !this.isMoving()) {
            return;
        }
        strafeEvent.setSprinting(this.isMovementKey(mc.options.keyJump));
        strafeEvent.setForward(GuiMove.getMovementSpeed(this.isMovementKey(mc.options.keyUp), this.isMovementKey(mc.options.keyDown)));
        strafeEvent.setStrafe(GuiMove.getMovementSpeed(this.isMovementKey(mc.options.keyLeft), this.isMovementKey(mc.options.keyRight)));
    }

    private boolean isMoving() {
        if (mc.screen instanceof ChatScreen) {
            return false;
        }
        if (mc.screen instanceof OldClickGui || mc.screen instanceof NewClickGui) {
            return true;
        }
        if (mc.player == null) {
            return false;
        }
        if (mc.player.containerMenu instanceof InventoryMenu) {
            return InventoryManager.isPerformingAction;
        }
        return false;
    }

    private boolean isMovementKey(KeyMapping keyMapping) {
        return InputConstants.isKeyDown(mc.getWindow().getWindow(), keyMapping.getDefaultKey().getValue());
    }

    private static float getMovementSpeed(boolean forward, boolean back) {
        if (forward == back) {
            return 0.0f;
        }
        return forward ? 1.0f : -1.0f;
    }
}