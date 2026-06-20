package shit.nilore.modules.impl.misc.ai;

import net.minecraft.util.Mth;
import shit.nilore.ClientBase;

public class MovementHelper extends ClientBase {

    public static void moveToward(double dx, double dz) {
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.01) {
            clearMovement();
            return;
        }
        float yaw = (float) (-Math.toDegrees(Math.atan2(dx, dz)));
        smoothYaw(yaw, 30f);
        mc.options.keyUp.setDown(true);
        mc.options.keyDown.setDown(false);
        mc.options.keyLeft.setDown(false);
        mc.options.keyRight.setDown(false);
    }

    public static void smoothYaw(float targetYaw, float maxStep) {
        float current = mc.player.getYRot();
        float diff = Mth.wrapDegrees(targetYaw - current);
        if (Math.abs(diff) < 0.5f) {
            mc.player.setYRot(targetYaw);
            return;
        }
        float step = Math.max(-maxStep, Math.min(diff, maxStep));
        float sens = mc.options.sensitivity().get().floatValue();
        float scaled = sens * 0.6f + 0.2f;
        float gcd = scaled * scaled * scaled * 1.2f;
        float result;
        if (Math.abs(step) >= Math.abs(diff) - 0.5f) {
            result = targetYaw - targetYaw % gcd;
        } else {
            result = current + step - (current + step) % gcd;
        }
        mc.player.setYRot(result);
    }

    public static void smoothPitch(float targetPitch, float maxStep) {
        float current = mc.player.getXRot();
        float diff = Mth.clamp(targetPitch - current, -90f, 90f);
        if (Math.abs(diff) < 0.5f) {
            mc.player.setXRot(targetPitch);
            return;
        }
        float step = Math.max(-maxStep, Math.min(diff, maxStep));
        float result = current + step;
        float sens = mc.options.sensitivity().get().floatValue();
        float scaled = sens * 0.6f + 0.2f;
        float gcd = scaled * scaled * scaled * 1.2f;
        if (gcd > 0 && Math.abs(diff) > gcd) {
            result = result - result % gcd;
        } else {
            result = targetPitch;
        }
        mc.player.setXRot(Mth.clamp(result, -90f, 90f));
    }

    public static void clearMovement() {
        mc.options.keyUp.setDown(false);
        mc.options.keyDown.setDown(false);
        mc.options.keyLeft.setDown(false);
        mc.options.keyRight.setDown(false);
        mc.options.keySprint.setDown(false);
        mc.options.keyJump.setDown(false);
    }
}
