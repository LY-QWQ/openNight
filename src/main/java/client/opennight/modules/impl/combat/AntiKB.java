package client.opennight.modules.impl.combat;

import java.util.Arrays;
import java.util.Optional;
import client.opennight.event.impl.DisconnectEvent;
import client.opennight.event.impl.GameTickEvent;
import client.opennight.event.impl.MotionEvent;
import client.opennight.event.impl.PreMotionEvent;
import client.opennight.event.impl.ReceivePacketEvent;
import client.opennight.event.impl.Render2DEvent;
import client.opennight.event.impl.RenderEvent;
import client.opennight.event.impl.RotationEvent;
import client.opennight.event.impl.SprintEvent;
import client.opennight.event.impl.StrafeEvent;
import client.opennight.event.impl.TickEvent;
import client.opennight.modules.Category;
import client.opennight.modules.Module;
import client.opennight.modules.impl.combat.antikb.AntiKBMode;
import client.opennight.modules.impl.movement.FireballBlink;
import client.opennight.modules.impl.movement.HighJump;
import client.opennight.settings.impl.BooleanSetting;
import client.opennight.settings.impl.ModeSetting;
import client.opennight.settings.impl.NumberSetting;
import client.opennight.utils.animation.Timer;
import client.opennight.utils.rotation.Rotation;
import client.opennight.event.EventTarget;

public class AntiKB
        extends Module {
    public static AntiKB INSTANCE;
    public static Rotation rotation;
    public static ModeSetting mode;
    public final BooleanSetting rotate = new BooleanSetting("Rotate", false, () -> mode.is("Jump Reset") || mode.is("Mix"));
    public final BooleanSetting tryAttack = new BooleanSetting("Try Attack", false, () -> mode.is("Mix"));
    public final BooleanSetting movementOverride = new BooleanSetting("Movement Override", false, () -> mode.is("Mix"));
    public final BooleanSetting followDirection = new BooleanSetting("Follow Direction", false, () -> mode.is("Jump Reset"));
    public final NumberSetting rotateTicks = new NumberSetting("Rotate Ticks", 12, 3, 20, 1, () -> mode.is("Jump Reset") && (this.rotate.getValue() != false || this.followDirection.getValue() != false));
    public final BooleanSetting autoAttackCount = new BooleanSetting("Auto Attack Count", true, () -> mode.is("NoXZ"));
    public final NumberSetting attackAmount = new NumberSetting("Attack amount", 5.0, 1.0, 20.0, 1, () -> mode.is("NoXZ") && !this.autoAttackCount.getValue());
    public final BooleanSetting instantAttack = new BooleanSetting("Instant Attack", false, () -> mode.is("NoXZ"));
    public final BooleanSetting sprintStateCheck = new BooleanSetting("Sprint state check", true, () -> mode.is("NoXZ"));
    public final BooleanSetting debugLog = new BooleanSetting("Debug Log", false);
    private final Timer grimSyncTimer = new Timer();
    public AntiKB() {
        super("AntiKB", Category.COMBAT);
        INSTANCE = this;
        AntiKBMode.initModes();
    }

    @Override
    public void onEnable() {
        this.grimSyncTimer.reset();
        Optional<AntiKBMode> optional;
        rotation = null;
        if (!Arrays.stream((Object[])mode.getModes()).toList().contains(mode.getValue())) {
            mode.withDefault("NoXZ");
        }
        if ((optional = AntiKBMode.findMode(mode.getValue())).isEmpty()) {
            return;
        }
        optional.get().onEnable();
    }

    @Override
    public void onDisable() {
        rotation = null;
        Optional<AntiKBMode> optional = AntiKBMode.findMode(mode.getValue());
        if (optional.isEmpty()) {
            return;
        }
        optional.get().onDisable();
    }

    @EventTarget
    public void onGameTick(GameTickEvent gameTickEvent) {
        Optional<AntiKBMode> optional = AntiKBMode.findMode(mode.getValue());
        if (FireballBlink.INSTANCE.isEnabled() || HighJump.INSTANCE.isEnabled() || optional.isEmpty()) {
            return;
        }
        optional.get().onGameTick(gameTickEvent);
    }

    @EventTarget
    public void onPreMotion(PreMotionEvent preMotionEvent) {
        Optional<AntiKBMode> optional = AntiKBMode.findMode(mode.getValue());
        if (FireballBlink.INSTANCE.isEnabled() || HighJump.INSTANCE.isEnabled() || optional.isEmpty()) {
            return;
        }
        optional.get().onPreMotion(preMotionEvent);
    }

    @EventTarget
    public void onTick(TickEvent tickEvent) {
        Optional<AntiKBMode> optional = AntiKBMode.findMode(mode.getValue());
        if (FireballBlink.INSTANCE.isEnabled() || HighJump.INSTANCE.isEnabled() || optional.isEmpty()) {
            return;
        }
        optional.get().onTick(tickEvent);
    }

    @EventTarget
    public void onSprint(SprintEvent sprintEvent) {
        Optional<AntiKBMode> optional = AntiKBMode.findMode(mode.getValue());
        if (FireballBlink.INSTANCE.isEnabled() || HighJump.INSTANCE.isEnabled() || optional.isEmpty()) {
            return;
        }
        optional.get().onSprint(sprintEvent);
    }

    @EventTarget
    public void onRotation(RotationEvent rotationEvent) {
        Optional<AntiKBMode> optional = AntiKBMode.findMode(mode.getValue());
        if (FireballBlink.INSTANCE.isEnabled() || HighJump.INSTANCE.isEnabled() || optional.isEmpty()) {
            return;
        }
        optional.get().onRotation(rotationEvent);
    }

    @EventTarget
    public void onMotion(MotionEvent motionEvent) {
        Optional<AntiKBMode> optional = AntiKBMode.findMode(mode.getValue());
        if (FireballBlink.INSTANCE.isEnabled() || HighJump.INSTANCE.isEnabled() || optional.isEmpty()) {
            return;
        }
        optional.get().onMotion(motionEvent);
    }

    @EventTarget(value=1)
    public void onReceivePacket(ReceivePacketEvent receivePacketEvent) {
        Optional<AntiKBMode> optional = AntiKBMode.findMode(mode.getValue());
        if (FireballBlink.INSTANCE.isEnabled() || HighJump.INSTANCE.isEnabled() || optional.isEmpty()) {
            return;
        }
        optional.get().onReceivePacket(receivePacketEvent);
    }

    @EventTarget
    public void onDisconnect(DisconnectEvent disconnectEvent) {
        Optional<AntiKBMode> optional = AntiKBMode.findMode(mode.getValue());
        if (optional.isEmpty()) {
            return;
        }
        optional.get().onDisconnect(disconnectEvent);
    }

    @EventTarget(value=3)
    public void onStrafe(StrafeEvent strafeEvent) {
        Optional<AntiKBMode> optional = AntiKBMode.findMode(mode.getValue());
        if (FireballBlink.INSTANCE.isEnabled() || HighJump.INSTANCE.isEnabled() || optional.isEmpty()) {
            return;
        }
        optional.get().onStrafe(strafeEvent);
    }

    @EventTarget
    public void onRender(RenderEvent renderEvent) {
        Optional<AntiKBMode> optional = AntiKBMode.findMode(mode.getValue());
        if (FireballBlink.INSTANCE.isEnabled() || HighJump.INSTANCE.isEnabled() || optional.isEmpty()) {
            return;
        }
        optional.get().onRender(renderEvent);
    }

    @EventTarget
    public void onRender2D(Render2DEvent render2DEvent) {
        Optional<AntiKBMode> optional = AntiKBMode.findMode(mode.getValue());
        if (FireballBlink.INSTANCE.isEnabled() || HighJump.INSTANCE.isEnabled() || optional.isEmpty()) {
            return;
        }
        optional.get().onRender2D(render2DEvent);
    }

    static {
        mode = new ModeSetting("Mode", "Jump Reset", "Mix", "NoXZ").withDefault("NoXZ");
    }
}