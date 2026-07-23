package client.opennight.modules.impl.render;

import client.opennight.modules.Category;
import client.opennight.modules.Module;

public class NoHurtCam
extends Module {
    public static NoHurtCam INSTANCE;
    public NoHurtCam() {
        super("NoHurtCam", Category.RENDER);
        INSTANCE = this;
    }
}