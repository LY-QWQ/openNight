package client.opennight.patch;

import asm.patchify.annotation.Inject;
import asm.patchify.annotation.Patch;
import net.minecraft.client.KeyboardHandler;
import client.opennight.NightClient;
import client.opennight.event.impl.KeyEvent;

@Patch(KeyboardHandler.class)
public class KeyboardHandlerPatch {
    @Inject(method = "keyPress", desc = "(JIIII)V")
    public static void onKeyPress(KeyboardHandler handler, long window, int keyCode, int scanCode, int action, int modifiers, CallbackInfo callbackInfo) {
        if (handler == null || !NightClient.isReady()) return;
        KeyEvent event = new KeyEvent(keyCode, action != 0);
        NightClient.getInstance().getEventBus().call(event);
        if (event.isCancelled()) {
            callbackInfo.cancelled = true;
        }
    }
}
