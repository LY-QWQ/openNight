package client.opennight.patch;

import asm.patchify.annotation.At;
import asm.patchify.annotation.Inject;
import asm.patchify.annotation.Overwrite;
import asm.patchify.annotation.Patch;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import client.opennight.ClientBase;
import client.opennight.NightClient;
import client.opennight.event.impl.PreTickEvent;
import client.opennight.event.impl.RayTraceEvent;
import client.opennight.event.impl.RotationEvent;
import client.opennight.event.impl.SneakEvent;
import client.opennight.event.impl.StuckInBlockEvent;
import client.opennight.utils.misc.ReflectionUtil;

@Patch(Entity.class)
public class EntityPatch {
    @Inject(method = "makeStuckInBlock", desc = "(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/phys/Vec3;)V", at = @At(At.Type.TAIL))
    public static void onMakeStuckInBlock(Entity entity, BlockState state, Vec3 motion, CallbackInfo callbackInfo) {
        if (ClientBase.mc.player != entity) return;
        StuckInBlockEvent event = new StuckInBlockEvent(state, motion);
        NightClient.getInstance().getEventBus().call(event);
        if (event.isCancelled()) {
            ReflectionUtil.setInstanceField(entity, Vec3.ZERO, "stuckSpeedMultiplier", "net/minecraft/world/entity/Entity");
            return;
        }
        ReflectionUtil.setInstanceField(entity, event.getMotion(), "stuckSpeedMultiplier", "net/minecraft/world/entity/Entity");
    }

    @Inject(method = "push", desc = "(Lnet/minecraft/world/entity/Entity;)V", at = @At(At.Type.HEAD))
    public static void onPush(Entity entity, CallbackInfo callbackInfo) {
        if (!NightClient.isReady() || entity != ClientBase.mc.player || entity.isInWater()) return;
        SneakEvent event = new SneakEvent();
        NightClient.getInstance().getEventBus().call(event);
        if (event.isCancelled()) {
            callbackInfo.cancel();
        }
    }

    @Overwrite(method = "moveRelative", desc = "(FLnet/minecraft/world/phys/Vec3;)V")
    public static void overwriteMoveRelative(Entity entity, float speed, Vec3 movement) throws Exception {
        RotationEvent event = new RotationEvent(entity.getYRot(), speed);
        if (NightClient.isReady() && entity == ClientBase.mc.player) {
            NightClient.getInstance().getEventBus().call(event);
        }
        float yaw = event.getYaw();
        Vec3 result = applyRotation(movement, speed, yaw);
        entity.setDeltaMovement(entity.getDeltaMovement().add(result));
        if (NightClient.isReady() && entity == ClientBase.mc.player) {
            NightClient.getInstance().getEventBus().call(new PreTickEvent());
        }
    }

    @Overwrite(method = "calculateViewVector", desc = "(FF)Lnet/minecraft/world/phys/Vec3;")
    public static Vec3 overwriteCalculateViewVector(Entity entity, float pitch, float yaw) throws Exception {
        RayTraceEvent event = new RayTraceEvent(entity, yaw, pitch);
        if (NightClient.isReady()) {
            NightClient.getInstance().getEventBus().call(event);
        }
        yaw = event.getYaw();
        pitch = event.getPitch();
        float pitchRad = pitch * (float) (Math.PI / 180.0);
        float yawRad = -yaw * (float) (Math.PI / 180.0);
        float cosYaw = Mth.cos(yawRad);
        float sinYaw = Mth.sin(yawRad);
        float cosPitch = Mth.cos(pitchRad);
        float sinPitch = Mth.sin(pitchRad);
        return new Vec3(sinYaw * cosPitch, -sinPitch, cosYaw * cosPitch);
    }

    public static Vec3 applyRotation(Vec3 movement, float speed, float yaw) {
        double lengthSq = movement.lengthSqr();
        if (lengthSq < 1.0e-7) {
            return Vec3.ZERO;
        }
        Vec3 normalized = (lengthSq > 1.0 ? movement.normalize() : movement).scale(speed);
        float sinYaw = Mth.sin(yaw * (float) (Math.PI / 180.0));
        float cosYaw = Mth.cos(yaw * (float) (Math.PI / 180.0));
        return new Vec3(normalized.x * cosYaw - normalized.z * sinYaw,
                normalized.y,
                normalized.z * cosYaw + normalized.x * sinYaw);
    }
}
