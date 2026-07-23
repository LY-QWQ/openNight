package client.opennight.modules.impl.render;

import client.opennight.modules.Category;
import client.opennight.modules.Module;
import client.opennight.settings.impl.NumberSetting;
import client.opennight.utils.misc.Triple;
import client.opennight.utils.misc.TripleProvider;

public class FullBright
extends Module
implements TripleProvider {
    public static FullBright INSTANCE;
    public final NumberSetting brightnessSetting = new NumberSetting("Brightness", 100.0f, 0.0f, 100.0f, 1.0f);

    public FullBright() {
        super("FullBright", Category.RENDER);
        INSTANCE = this;
    }

    @Override
    public Triple getTriple() {
        if (this.isEnabled()) {
            return new Triple(this.getName(), String.valueOf(this.brightnessSetting.getValue().intValue()), true);
        }
        return null;
    }
}