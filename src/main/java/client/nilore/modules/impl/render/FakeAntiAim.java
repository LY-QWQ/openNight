package client.nilore.modules.impl.render;

import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.settings.impl.NumberSetting;

public class FakeAntiAim extends Module {

    public static FakeAntiAim INSTANCE;

    public final NumberSetting spinSpeed = new NumberSetting("Spin Speed", 10, 0, 1000, 1);

    public FakeAntiAim() {
        super("FakeAntiAim", Category.RENDER);
        INSTANCE = this;
    }
}
