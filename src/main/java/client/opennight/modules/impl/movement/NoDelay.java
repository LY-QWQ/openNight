package client.opennight.modules.impl.movement;

import client.opennight.modules.Category;
import client.opennight.modules.Module;
import client.opennight.settings.impl.BooleanSetting;

public class NoDelay
extends Module {
    public static NoDelay INSTANCE;
    public final BooleanSetting fastDig = new BooleanSetting("No Jump Delay", true);

    public NoDelay() {
        super("NoDelay", Category.MOVEMENT);
        INSTANCE = this;
    }
}