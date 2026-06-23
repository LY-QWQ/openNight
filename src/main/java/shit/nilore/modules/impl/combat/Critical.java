package shit.nilore.modules.impl.combat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import shit.nilore.ClientBase;
import shit.nilore.event.EventTarget;
import shit.nilore.event.impl.PreMotionEvent;
import shit.nilore.event.impl.TickEvent;
import shit.nilore.modules.Category;
import shit.nilore.modules.Module;
import shit.nilore.settings.impl.BooleanSetting;
import shit.nilore.settings.impl.NumberSetting;

public class Critical
        extends Module {
    public static Critical INSTANCE;

    public final NumberSetting range = new NumberSetting("Range", 3.0, 1.0, 3.2, 0.1);
    public final BooleanSetting autoJump = new BooleanSetting("Auto Jump", true);

    public Critical() {
        super("Critical", Category.COMBAT);
        INSTANCE = this;
    }

    @Override
    public void onDisable() {
        super.onDisable();
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.level == null) return;
        if (!autoJump.getValue()) return;

        KillAura aura = KillAura.INSTANCE;
        if (aura == null || !aura.isEnabled()) return;

        Entity target = KillAura.target;
        if (target == null) return;

        if (mc.options.keyJump.isDown()) return;

        if (mc.player.onGround() && mc.player.distanceTo(target) <= 3.0f) {
            mc.player.jumpFromGround();
        }
    }

    @EventTarget
    public void onPreMotion(PreMotionEvent event) {
        if (mc.player == null) return;

        KillAura aura = KillAura.INSTANCE;
        if (aura == null || !aura.isEnabled()) return;

        Entity target = KillAura.target;
        if (target == null) return;

        if (cantCrit(target)) {
            return;
        }

        // skipTicks: 下落且目标在范围内时，每帧添加空 Runnable 到 delayPackets 延迟玩家 tick，
        // 让服务器认为玩家仍在坠落，确保暴击生效（等价于 Candy 的 Client.skipTicks）
        float dist = mc.player.distanceTo(target);
        if (dist <= range.getValue().floatValue()
                && mc.player.getDeltaMovement().y < 0.0
                && !mc.player.onGround()) {
            ClientBase.delayPackets.add(() -> {});
            if (mc.player.isSprinting()) {
                mc.player.setSprinting(false);
            }
        }
    }

    private boolean cantCrit(Entity target) {
        if (mc.player == null) return true;
        if (!(target instanceof LivingEntity living)) return true;

        return mc.player.onClimbable()
                || mc.player.isInWater()
                || mc.player.isInLava()
                || mc.player.isPassenger()
                || living.hurtTime > 10
                || living.getHealth() <= 0.0f;
    }
}