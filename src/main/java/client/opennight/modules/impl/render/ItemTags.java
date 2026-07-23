package client.opennight.modules.impl.render;

import client.opennight.modules.Category;
import client.opennight.modules.Module;

public class ItemTags extends Module {
    public static ItemTags INSTANCE;

    public ItemTags() {
        super("ItemTags", Category.RENDER);
        INSTANCE = this;
    }
}
