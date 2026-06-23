package shit.nilore.modules.impl.render;

import net.minecraft.world.level.block.Block;
import shit.nilore.modules.Category;
import shit.nilore.modules.Module;

public class XRay extends Module {
    public static XRay INSTANCE;

    public XRay() {
        super("XRay", Category.RENDER);
        INSTANCE = this;
    }

    public boolean isXrayVisible(Block block) {
        // TODO: implement actual XRay visibility logic (ore detection, etc.)
        return true;
    }
}
