package client.opennight.modules.impl.player.helper;

import lombok.Getter;
import client.opennight.ClientBase;
import client.opennight.event.impl.MotionEvent;
import client.opennight.event.impl.PreMotionEvent;
import client.opennight.event.impl.RenderEvent;
import client.opennight.event.impl.TickEvent;
import client.opennight.utils.rotation.Rotation;

public abstract class HelperBase
extends ClientBase {
    @Getter
    private final String name;

    public HelperBase(String string) {
        this.name = string;
    }

    public void onEnable() {
    }

    public void onDisable() {
    }

    public void onTick(TickEvent tickEvent) {
    }

    public void onMotion(MotionEvent motionEvent) {
    }

    public void onRender(RenderEvent renderEvent) {
    }

    public void onPreMotion(PreMotionEvent preMotionEvent) {
    }

    public boolean isActive() {
        return false;
    }

    public Rotation getTargetRotation() {
        return null;
    }

    }