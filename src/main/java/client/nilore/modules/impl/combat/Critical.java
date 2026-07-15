package client.nilore.modules.impl.combat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import client.nilore.event.EventTarget;
import client.nilore.event.impl.SprintEvent;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.settings.impl.BooleanSetting;
import client.nilore.settings.impl.NumberSetting;

public class Critical extends Module {
    public static Critical INSTANCE;

    public final BooleanSetting controlSprintKey = new BooleanSetting("Control Sprint Key", true);
    public final NumberSetting hurtTime = new NumberSetting("Hurt Time", 2, 0, 2, 1);

    public Critical() {
        super("Critical", Category.COMBAT);
        INSTANCE = this;
    }

    @EventTarget
    public void onSprint(SprintEvent event) {
        if (mc.player == null
                || event == null
                || !event.isSprint()
                || event.getSource() != SprintEvent.Source.INPUT
                || mc.player.getDeltaMovement().y() > -0.08
                || mc.player.onGround()
                || mc.player.isInWaterRainOrBubble()
                || mc.player.isInLava()
                || mc.player.onClimbable()
                || KillAura.INSTANCE == null
                || !KillAura.INSTANCE.isEnabled()) {
            return;
        }

        Entity target = KillAura.INSTANCE.getTarget();
        if (!(target instanceof LivingEntity livingTarget)
                || livingTarget.hurtTime < 0
                || livingTarget.hurtTime > hurtTime.getValue().intValue()) {
            return;
        }

        if (controlSprintKey.getValue()) {
            mc.options.keySprint.setDown(false);
        } else {
            event.setSprint(false);
        }
    }
}
