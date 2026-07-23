package client.opennight.modules.impl.render;

import net.minecraft.world.level.block.Block;
import client.opennight.modules.Category;
import client.opennight.modules.Module;

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
