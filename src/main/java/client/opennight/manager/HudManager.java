package client.opennight.manager;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import client.opennight.ClientBase;
import client.opennight.NightClient;
import client.opennight.event.impl.GlRenderEvent;
import client.opennight.event.impl.Render2DEvent;
import client.opennight.event.impl.TickEvent;
import client.opennight.gui.IntroAnimation;
import client.opennight.hud.HudElement;
import client.opennight.hud.KeyBindsHud;
import client.opennight.hud.LieDetector;
import client.opennight.hud.MusicPlayerHud;
import client.opennight.hud.ModuleListHud;
import client.opennight.hud.NotificationHud;
import client.opennight.hud.PlayerListHud;
import client.opennight.hud.PotionEffectsHud;
import client.opennight.hud.ScoreboardHud;
import client.opennight.hud.TargetHud;
import client.opennight.modules.impl.render.Armor;
import client.opennight.event.EventTarget;

public class HudManager {
    private final Map<String, HudElement> hudElements = new HashMap<>();

    public HudManager() {
        this.init();
    }

    public void init() {
        this.registerHudElement(new TargetHud());
        this.registerHudElement(new KeyBindsHud());
        this.registerHudElement(new ModuleListHud());
        this.registerHudElement(new PlayerListHud());
        this.registerHudElement(new PotionEffectsHud());
        this.registerHudElement(new LieDetector());
        this.registerHudElement(new MusicPlayerHud());
        this.registerHudElement(new NotificationHud());
        this.registerHudElement(new Armor());
        this.registerHudElement(new ScoreboardHud());
    }

    private void registerHudElement(HudElement hudElement) {
        NightClient.getInstance().getModuleManager().register(hudElement);
        this.hudElements.put(hudElement.getClass().getSimpleName(), hudElement);
    }

    public <T extends HudElement> T getHudElement(Class<T> clazz) {
        return clazz.cast(this.hudElements.get(clazz.getSimpleName()));
    }

    public HudElement getHudElementByName(String string) {
        return this.hudElements.values().stream().filter(hudElement -> hudElement.getName().equalsIgnoreCase(string)).findFirst().orElse(null);
    }

    public Collection<HudElement> getHudElements() {
        return this.hudElements.values();
    }

    @EventTarget
    public void onTick(TickEvent tickEvent) {
        if (ClientBase.mc.screen == null) {
            try {
                for (HudElement hudElement : NightClient.getInstance().getHudManager().getHudElements()) {
                    hudElement.stopDragging();
                }
            } catch (Exception exception) {
                ClientBase.logger.error(exception);
                ClientBase.logger.error(exception.getMessage());
            }
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent render2DEvent) {
        if (IntroAnimation.isRunning()) {
            return;
        }
        for (HudElement hudElement : this.getHudElements()) {
            if (!hudElement.isEnabled()) continue;
            hudElement.onRender2D(render2DEvent, hudElement.getX(), hudElement.getY());
        }
    }

    @EventTarget
    public void onGlRender(GlRenderEvent glRenderEvent) {
        if (IntroAnimation.isRunning()) {
            return;
        }
        for (HudElement hudElement : this.getHudElements()) {
            if (!hudElement.isEnabled()) continue;
            hudElement.onGlRender(glRenderEvent, hudElement.getX(), hudElement.getY());
        }
    }
}