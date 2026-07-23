package client.opennight.modules.impl.render;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import client.opennight.gui.MaterialClickGui;
import client.opennight.gui.NewClickGui;
import client.opennight.gui.OldClickGui;
import client.opennight.gui.PanelClickGui;
import client.opennight.modules.Category;
import client.opennight.modules.Module;
import client.opennight.settings.impl.ModeSetting;

public class ClickGuiModule
extends Module {
    public static final Logger LOGGER = LogManager.getLogger(ClickGuiModule.class);
    public final ModeSetting styleSetting = new ModeSetting("Mode", "Old", "Panel", "New", "Material3").withDefault("Old");
    public final ModeSetting materialTheme = new ModeSetting("Material Theme", "Dark", "Light")
            .withVisibility(() -> this.styleSetting.is("Material3"));

    public ClickGuiModule() {
        super("ClickGui", Category.RENDER, 344);
    }

    @Override
    protected void onEnable() {
        try {
            if (this.styleSetting.is("Old")) {
                mc.setScreen(new OldClickGui());
            } else if (this.styleSetting.is("Panel")) {
                mc.setScreen(PanelClickGui.panelClickGui);
            } else if (this.styleSetting.is("Material3")) {
                mc.setScreen(MaterialClickGui.instance);
            } else {
                mc.setScreen(new NewClickGui());
            }
            LOGGER.info("ClickGUI opened successfully");
        } catch (Exception exception) {
            LOGGER.error("Error opening ClickGUI", exception);
        } finally {
            this.setEnabled(false);
        }
    }
}