package client.opennight.gui.newclickgui;

import lombok.Getter;
import lombok.Generated;
import client.opennight.gui.newclickgui.CategoryPanel;
import client.opennight.gui.newclickgui.UIElement;
import client.opennight.settings.Setting;
import client.opennight.utils.animation.SmoothAnimationTimer;

public abstract class SettingElement<T extends Setting<?>>
extends UIElement {
    @Getter
    protected final CategoryPanel parentPanel;
    @Getter
    protected final T setting;
    @Getter
    protected final SmoothAnimationTimer visibilityTimer = new SmoothAnimationTimer();

    @Generated
    public SettingElement(CategoryPanel categoryPanel, T setting) {
        this.parentPanel = categoryPanel;
        this.setting = setting;
    }
}