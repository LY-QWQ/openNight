package shit.nilore.modules.impl.render;

import shit.nilore.modules.Category;
import shit.nilore.modules.Module;
import shit.nilore.settings.impl.NumberSetting;

public class FakeAntiAim extends Module {

    public static FakeAntiAim INSTANCE;

    public final NumberSetting spinSpeed = new NumberSetting("Spin Speed", 10, 0, 1000, 1);

    public FakeAntiAim() {
        super("FakeAntiAim", Category.RENDER);
        INSTANCE = this;
    }
}
