package shit.nilore.patch;

import asm.patchify.annotation.At;
import asm.patchify.annotation.Inject;
import asm.patchify.annotation.Patch;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import shit.nilore.NiloreClient;
import shit.nilore.modules.impl.render.EntityEditor;
import shit.nilore.modules.impl.render.FakeAntiAim;
import shit.nilore.utils.render.EntityUtil;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Patch(EntityRenderDispatcher.class)
public class EntityRenderDispatcherPatch {

    private static final ThreadLocal<Boolean> IS_RENDERING_FAKE = ThreadLocal.withInitial(() -> false);
    private static final Map<Integer, float[]> LAUNCH_ROTATION_CACHE = new ConcurrentHashMap<>();

    @Inject(
            method = "render",
            desc = "(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(At.Type.HEAD)
    )
    public static void onRender(
            EntityRenderDispatcher dispatcher, Entity entity,
            double x, double y, double z, float yaw, float partialTick,
            com.mojang.blaze3d.vertex.PoseStack poseStack,
            net.minecraft.client.renderer.MultiBufferSource buffer, int light,
            CallbackInfo ci
    ) {
        if (IS_RENDERING_FAKE.get()) return;

        Minecraft mc = Minecraft.getInstance();
        if (!NiloreClient.isReady()) return;

        boolean shouldProcessEditor = EntityEditor.INSTANCE != null && EntityEditor.INSTANCE.isEnabled();

        if (shouldProcessEditor) {
            if (!(entity instanceof net.minecraft.world.entity.player.Player) && !EntityEditor.INSTANCE.getAllEntity()) shouldProcessEditor = false;
            if (entity.isInvisible()) shouldProcessEditor = false;
            if (entity == mc.player && EntityEditor.INSTANCE.getIgnoreSelf()) shouldProcessEditor = false;
            if (EntityEditor.INSTANCE.getExcludeDrops() && (entity instanceof ItemEntity || entity instanceof ExperienceOrb)) shouldProcessEditor = false;

            if (shouldProcessEditor) {
                String target = EntityEditor.INSTANCE.getTargetEntity();
                Entity fake = EntityUtil.getOrCreateSyncedEntity(entity, target);
                if (fake != null) {
                    ci.cancel();

                    IS_RENDERING_FAKE.set(true);
                    try {
                        poseStack.pushPose();

                        if ((entity instanceof ItemEntity || entity instanceof ExperienceOrb) && EntityEditor.INSTANCE.getBetterItemView()) {
                            poseStack.translate(x, y, z);
                            float speedFactor = (entity instanceof net.minecraft.world.entity.ExperienceOrb) ? 20.0F : 10.0F;
                            float itemRotation = (entity.tickCount + partialTick) * speedFactor;
                            poseStack.mulPose(Axis.YP.rotationDegrees(itemRotation));
                            float scaleFactor = (entity instanceof net.minecraft.world.entity.ExperienceOrb) ? 0.2F : 0.45F;
                            poseStack.scale(scaleFactor, scaleFactor, scaleFactor);
                            poseStack.translate(-x, -y, -z);
                        }
                        else if (entity instanceof Projectile projectile && EntityEditor.INSTANCE.getBetterItemView()) {
                            int id = projectile.getId();
                            if (projectile.isRemoved()) {
                                LAUNCH_ROTATION_CACHE.remove(id);
                            } else {
                                if (projectile.tickCount <= 1 && !LAUNCH_ROTATION_CACHE.containsKey(id)) {
                                    Entity owner = projectile.getOwner();
                                    if (owner != null) {
                                        LAUNCH_ROTATION_CACHE.put(id, new float[]{owner.getYRot(), owner.getXRot()});
                                    }
                                }
                            }

                            float[] launchRot = LAUNCH_ROTATION_CACHE.get(id);
                            if (launchRot != null) {
                                float launchYaw = launchRot[0];
                                float launchPitch = launchRot[1];

                                poseStack.translate(x, y, z);
                                poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - launchYaw));
                                poseStack.mulPose(Axis.XP.rotationDegrees(launchPitch));

                                boolean isLongProjectile = (projectile instanceof AbstractArrow) || (projectile instanceof ThrownTrident);
                                if (isLongProjectile) {
                                    poseStack.scale(0.45F, 0.45F, 1.2F);
                                } else {
                                    poseStack.scale(0.45F, 0.45F, 0.45F);
                                }
                                poseStack.translate(-x, -y, -z);
                                yaw = launchYaw;
                            } else {
                                poseStack.translate(x, y, z);
                                poseStack.scale(0.45F, 0.45F, 0.45F);
                                poseStack.translate(-x, -y, -z);
                            }
                        }

                        if (entity == mc.player && FakeAntiAim.INSTANCE != null && FakeAntiAim.INSTANCE.isEnabled()) {
                            poseStack.translate(x, y, z);
                            int spinSpeed = FakeAntiAim.INSTANCE.spinSpeed.getValue().intValue();
                            float currentSpin = (entity.tickCount + partialTick) * spinSpeed;
                            poseStack.mulPose(Axis.YP.rotationDegrees(currentSpin));
                            poseStack.translate(-x, -y, -z);
                            yaw += currentSpin;
                        }

                        dispatcher.render(fake, x, y, z, yaw, partialTick, poseStack, buffer, light);
                        poseStack.popPose();
                    } catch (Exception ignored) {
                    } finally {
                        IS_RENDERING_FAKE.set(false);
                    }
                    return;
                }
            }
        }

        if (FakeAntiAim.INSTANCE != null && FakeAntiAim.INSTANCE.isEnabled() && entity == mc.player) {
            ci.cancel();

            IS_RENDERING_FAKE.set(true);
            try {
                poseStack.pushPose();

                poseStack.translate(x, y, z);
                int spinSpeed = FakeAntiAim.INSTANCE.spinSpeed.getValue().intValue();
                float currentSpin = (entity.tickCount + partialTick) * spinSpeed;
                poseStack.mulPose(Axis.YP.rotationDegrees(currentSpin));
                poseStack.translate(-x, -y, -z);

                dispatcher.render(entity, x, y, z, yaw + currentSpin, partialTick, poseStack, buffer, light);

                poseStack.popPose();
            } catch (Exception ignored) {
            } finally {
                IS_RENDERING_FAKE.set(false);
            }
        }
    }
}
