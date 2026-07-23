package client.opennight.modules.impl.movement;

import java.util.Random;
import client.opennight.NightClient;
import client.opennight.event.impl.TickEvent;
import client.opennight.modules.Category;
import client.opennight.modules.Module;
import client.opennight.settings.impl.ModeSetting;
import client.opennight.settings.impl.NumberSetting;
import client.opennight.utils.animation.Timer;
import client.opennight.event.EventTarget;

public class GameTimer extends Module {
    private final ModeSetting timerMode = new ModeSetting("Mode", "Constant", "MicroPulse").withDefault("MicroPulse");
    private final NumberSetting timerSpeed = new NumberSetting("Timer Speed", 1.05, 1.0, 2.0, 0.01);

    private final Timer stepTimer = new Timer();
    private final Random random = new Random();
    private int microPulseStep = 0;
    private long nextStepDelay = 0;

    private static final float[] MICRO_PULSE_SPEEDS = {1.0f, 1.05f, 1.0f, 1.03f};

    public Timer() {
        super("GameTimer", Category.MOVEMENT);
    }

    @Override
    public void onEnable() {
        this.microPulseStep = 0;
        this.nextStepDelay = this.randomStepDelay();
    }

    @Override
    public void onDisable() {
        NightClient.serverTickRate = 1.0f;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null) return;

        if (this.timerMode.is("Constant")) {
            NightClient.serverTickRate = this.timerSpeed.getValue().floatValue();
            return;
        }

        // MicroPulse mode: only active when moving
        if (!isMoving()) {
            NightClient.serverTickRate = 1.0f;
            this.microPulseStep = 0;
            this.nextStepDelay = this.randomStepDelay();
            return;
        }

        if (this.stepTimer.hasPassed(this.nextStepDelay)) {
            this.stepTimer.reset();
            this.microPulseStep = (this.microPulseStep + 1) % MICRO_PULSE_SPEEDS.length;
            this.nextStepDelay = this.randomStepDelay();
        }

        NightClient.serverTickRate = MICRO_PULSE_SPEEDS[this.microPulseStep];
    }

    private boolean isMoving() {
        return mc.player != null && mc.player.getDeltaMovement().lengthSqr() > 0.001;
    }

    private long randomStepDelay() {
        return 300 + this.random.nextInt(501); // 300-800ms
    }
}
