package client.opennight.modules.impl.combat;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Squid;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import client.opennight.ClientBase;
import client.opennight.NightClient;
import client.opennight.event.impl.PreMotionEvent;
import client.opennight.event.impl.RenderEvent;
import client.opennight.event.impl.SprintEvent;
import client.opennight.event.impl.TickEvent;
import client.opennight.event.impl.WorldChangeEvent;
import client.opennight.hud.ModuleListHud;
import client.opennight.modules.Category;
import client.opennight.modules.Module;
import client.opennight.modules.impl.combat.antikb.NoXZMode;
import client.opennight.modules.impl.movement.Scaffold;
import client.opennight.modules.impl.player.AntiTNT;
import client.opennight.modules.impl.player.AntiWeb;
import client.opennight.modules.impl.player.AutoWebPlace;
import client.opennight.modules.impl.player.Helper;
import client.opennight.modules.impl.player.MidPearl;
import client.opennight.modules.impl.player.Stuck;
import client.opennight.modules.impl.world.Teams;
import client.opennight.settings.impl.BooleanSetting;
import client.opennight.settings.impl.ModeSetting;
import client.opennight.settings.impl.NumberSetting;
import client.opennight.utils.game.EntityUtil;
import client.opennight.utils.game.ItemUtil;
import client.opennight.utils.game.RotationUtil;
import client.opennight.utils.math.MathUtil;
import client.opennight.utils.misc.ChatUtil;
import client.opennight.utils.misc.Assets;
import client.opennight.utils.render.RenderUtil;
import client.opennight.utils.rotation.Rotation;
import client.opennight.utils.rotation.RotationHandler;
import client.opennight.event.EventTarget;

public class KillAura extends Module {
    public static KillAura INSTANCE;
    public static Entity target;
    public static Entity aimingTarget;
    public static List<Entity> targetList = new ArrayList<>();

    private static final ResourceLocation NURIK_CAPTURE_TEXTURE = ResourceLocation.tryParse("opennight:nurik/capture");
    private static final String NURIK_CAPTURE_ASSET = "/assets/opennight/nurik/capture.png";
    private static boolean nurikTextureLoaded;
    private static boolean nurikTextureLoadFailed;

    // Fields kept in sync with the obfuscated jar: 13 BooleanSetting / 7
    // NumberSetting / 3 ModeSetting, in declaration order.
    public final BooleanSetting attackPlayer    = new BooleanSetting("Attack Player", true);
    public final BooleanSetting attackInvisible = new BooleanSetting("Attack Invisible", true);
    public final BooleanSetting attackAnimals   = new BooleanSetting("Attack Animals", false);
    public final BooleanSetting attackMobs      = new BooleanSetting("Attack Mobs", false);
    public final BooleanSetting multiAttack     = new BooleanSetting("Multi Attack", false);
    public final BooleanSetting infSwitch       = new BooleanSetting("Infinity Switch", false);
    public final BooleanSetting preferBaby      = new BooleanSetting("Prefer Baby", false);
    public final BooleanSetting morePart        = new BooleanSetting("More Particles", false);
    public final BooleanSetting keepSprint      = new BooleanSetting("Keep Sprint", true);
    public final BooleanSetting fix             = new BooleanSetting("Fix", false);
    public final BooleanSetting overrideRaycast = new BooleanSetting("Override Raycast", true);
    public final BooleanSetting ignoreSkipTicks = new BooleanSetting("Ignore skip ticks", false);
    public final BooleanSetting fakeAutoBlock   = new BooleanSetting("Fake AutoBlock", true);
    public final BooleanSetting test            = new BooleanSetting("Test", false);
    public final NumberSetting aimRange    = new NumberSetting("Aim Range", 3.0, 1.0, 6.0, 0.1);
    public final NumberSetting maxAps      = new NumberSetting("Max APS", 12.0, 1.0, 20.0, 1.0);
    public final NumberSetting minAps      = new NumberSetting("Min APS", 9.0, 1.0, 20.0, 1.0);
    public final NumberSetting switchSize  = new NumberSetting("Switch Size", 1.0, 1.0, 5.0, 1.0,
            () -> !(Boolean) this.infSwitch.getValue());
    public final NumberSetting switchDelay = new NumberSetting("Switch Delay (Attack Times)", 1.0, 1.0, 10.0, 1.0);
    public final NumberSetting fov         = new NumberSetting("FoV", 360.0, 10.0, 360.0, 1.0);
    public final NumberSetting hurtTime    = new NumberSetting("Hurt Time", 10.0, 0.0, 10.0, 1.0);
    public final ModeSetting delayMode    = new ModeSetting("Delay Mode", "1.8", "1.9").withDefault("1.8");
    public final ModeSetting priorityMode = new ModeSetting("Priority", "Distance", "FoV", "Health", "None").withDefault("FoV");
    public final ModeSetting targetEsp    = new ModeSetting("Target ESP", "None", "Spiral", "Box", "Tab", "NurikZapen").withDefault("Spiral");
    public final ModeSetting cpsMode      = new ModeSetting("CPS Mode", "Uniform", "Organic").withDefault("Uniform");
    public final BooleanSetting reachProtect    = new BooleanSetting("Reach Protect", true);
    public final NumberSetting maxReach         = new NumberSetting("Max Reach", 3.08, 2.0, 4.0, 0.01);
    public final BooleanSetting dynamicAimSpeed = new BooleanSetting("Dynamic Aim Speed", true);
    public final BooleanSetting organicBlock    = new BooleanSetting("Organic Block", true);

    public final BooleanSetting predictionEnabled  = new BooleanSetting("Prediction", true);
    public final NumberSetting enemyDelayThreshold = new NumberSetting("Enemy Delay Ticks", 4, 1, 5, 1,
            () -> (Boolean) this.predictionEnabled.getValue());
    public final NumberSetting selfDelayThreshold  = new NumberSetting("Self Delay Ticks", 2, 1, 5, 1,
            () -> (Boolean) this.predictionEnabled.getValue());

    public final NumberSetting rotationSpeed = new NumberSetting("Rotation Speed", 180, 0, 720, 5);
    public final NumberSetting rotationDrift = new NumberSetting("Drift", 0.1, 0, 5, 0.1);
    public final NumberSetting rotationJitter = new NumberSetting("Jitter", 0.02, 0, 1, 0.01);

    private RotationUtil.BestHitInfo currentBestHit;
    private RotationUtil.BestHitInfo prevBestHit;
    private int attackTimes;
    private float attacks;
    private int targetIndex;
    public int sprintTickCounter;
    private int sprintCounter;
    public Rotation rotation;

    // CPS rhythm phase: 0=normal, 1=burst, 2=pause
    private int cpsPhase;
    private double currentBlockDuration = 0.2;
    private long preBlockDelayNanos;
    private boolean skipBlockThisCycle;

    private Random organicRandom;
    private double organicTimeAccumulator;
    private double orgFreqYaw1, orgFreqYaw2, orgFreqPitch1, orgFreqPitch2;
    private double orgPhaseYaw1, orgPhaseYaw2, orgPhasePitch1, orgPhasePitch2;

    public KillAura() {
        super("KillAura", Category.COMBAT);
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        this.rotation = null;
        this.reinitOrganicModel();
        this.targetIndex = 0;
        this.attacks = 0.0f;
        this.cpsPhase = 0;
        this.currentBlockDuration = 0.2;
        this.preBlockDelayNanos = 0;
        this.skipBlockThisCycle = false;
        target = null;
        aimingTarget = null;
        targetList.clear();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        this.attacks = 0.0f;
        target = null;
        aimingTarget = null;
        this.sprintTickCounter = 0;
        this.sprintCounter = 0;
        this.attackTimes = 0;
        this.rotation = null;
        super.onDisable();
    }

    private void reinitOrganicModel() {
        this.organicRandom = new Random(System.nanoTime());
        this.organicTimeAccumulator = 0.0;
        this.orgFreqYaw1 = this.organicRandom.nextDouble() * 0.3 + 0.1;
        this.orgFreqYaw2 = this.organicRandom.nextDouble() * 0.5 + 0.5;
        this.orgFreqPitch1 = this.organicRandom.nextDouble() * 0.3 + 0.1;
        this.orgFreqPitch2 = this.organicRandom.nextDouble() * 0.5 + 0.5;
        this.orgPhaseYaw1 = this.organicRandom.nextDouble() * Math.PI * 2;
        this.orgPhaseYaw2 = this.organicRandom.nextDouble() * Math.PI * 2;
        this.orgPhasePitch1 = this.organicRandom.nextDouble() * Math.PI * 2;
        this.orgPhasePitch2 = this.organicRandom.nextDouble() * Math.PI * 2;
    }

    private Rotation applyOrganicRotation(Rotation from, Rotation to, float timeDelta) {
        float rawYawDelta = Mth.wrapDegrees(to.getYaw() - from.getYaw());
        float rawPitchDelta = to.getPitch() - from.getPitch();

        double speed = this.rotationSpeed.getValue().doubleValue();

        // Dynamic aim speed: float rotation speed based on distance to target
        if (this.dynamicAimSpeed.getValue() && target != null) {
            double distToTarget = mc.player.distanceTo(target);
            double distanceMultiplier;
            if (distToTarget > 3.0) {
                distanceMultiplier = MathUtil.randomDouble(0.7, 0.9);
            } else if (distToTarget >= 1.5) {
                distanceMultiplier = MathUtil.randomDouble(0.9, 1.1);
            } else {
                distanceMultiplier = MathUtil.randomDouble(1.0, 1.3);
            }
            speed *= distanceMultiplier;
        }

        double driftIntensity = this.rotationDrift.getValue().doubleValue();
        double jitterIntensity = this.rotationJitter.getValue().doubleValue();

        // speed=0: 瞬转，不加漂移抖动
        if (speed <= 0) {
            return to;
        }

        float deltaYaw = rawYawDelta * timeDelta;
        float deltaPitch = rawPitchDelta * timeDelta;

        double distance = Math.sqrt(deltaYaw * deltaYaw + deltaPitch * deltaPitch);
        if (distance < driftIntensity) {
            return new Rotation(from.getYaw() + deltaYaw, from.getPitch() + deltaPitch);
        }

        if (distance > 0) {
            double ratioYaw = Math.abs(deltaYaw) / distance;
            double ratioPitch = Math.abs(deltaPitch) / distance;
            double maxYaw = speed * ratioYaw * timeDelta;
            double maxPitch = speed * ratioPitch * timeDelta;
            deltaYaw = Mth.clamp(deltaYaw, (float)-maxYaw, (float)maxYaw);
            deltaPitch = Mth.clamp(deltaPitch, (float)-maxPitch, (float)maxPitch);
        }

        this.organicTimeAccumulator += timeDelta;

        double sinYaw = Math.sin(this.organicTimeAccumulator * this.orgFreqYaw1 + this.orgPhaseYaw1)
                + (this.organicRandom.nextDouble() * 0.1 + 0.45) * Math.sin(this.organicTimeAccumulator * this.orgFreqYaw2 + this.orgPhaseYaw2);
        double sinPitch = Math.sin(this.organicTimeAccumulator * this.orgFreqPitch1 + this.orgPhasePitch1)
                + (this.organicRandom.nextDouble() * 0.1 + 0.45) * Math.sin(this.organicTimeAccumulator * this.orgFreqPitch2 + this.orgPhasePitch2);
        double driftYaw = sinYaw * driftIntensity * timeDelta;
        double driftPitch = sinPitch * driftIntensity * timeDelta;

        double jitterYaw = (this.organicRandom.nextDouble() * 2 - 1) * jitterIntensity * timeDelta;
        double jitterPitch = (this.organicRandom.nextDouble() * 2 - 1) * jitterIntensity * timeDelta;

        float moveYaw = deltaYaw + (float)driftYaw + (float)jitterYaw;
        float movePitch = deltaPitch + (float)driftPitch + (float)jitterPitch;

        float finalYaw = from.getYaw() + moveYaw;
        float finalPitch = Mth.clamp(from.getPitch() + movePitch, -90.0f, 90.0f);
        return patchConstantRotation(new Rotation(finalYaw, finalPitch), from);
    }

    /**
     * GCD 对齐：将旋转增量取整到灵敏度步长的整数倍，
     * 防止对静止目标产生微抖。等价于 Candy 的 RotationUtility.patchConstantRotation。
     */
    private static Rotation patchConstantRotation(Rotation rotation, Rotation prevRotation) {
        double sensitivity = mc.options.sensitivity().get().floatValue() * 0.6 + 0.2;
        double multiplier = (sensitivity * sensitivity * sensitivity) * 8.0;
        double divisor = multiplier * 0.15;

        float yawDelta = rotation.getYaw() - prevRotation.getYaw();
        float pitchDelta = rotation.getPitch() - prevRotation.getPitch();
        float yaw = prevRotation.getYaw() + (float)(Math.round(yawDelta / divisor) * divisor);
        float pitch = prevRotation.getPitch() + (float)(Math.round(pitchDelta / divisor) * divisor);
        return new Rotation(yaw, pitch);
    }

    @EventTarget
    public void onWorldChange(WorldChangeEvent event) {
        target = null;
        aimingTarget = null;
        this.attacks = 0.0f;
        this.setEnabled(false);
    }

    @EventTarget
    public void onRender(RenderEvent event) {
        if (this.targetEsp.is("None")) return;
        Entity entity = aimingTarget;
        if (entity == null || mc.gameRenderer == null) return;
        PoseStack poseStack = event.poseStack();
        poseStack.pushPose();
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 cameraPos = camera.getPosition();
        poseStack.translate(-cameraPos.x(), -cameraPos.y(), -cameraPos.z());

        double dx = entity.getX() - entity.xOld;
        double dy = entity.getY() - entity.yOld;
        double dz = entity.getZ() - entity.zOld;
        Vec3 playerDelta = mc.player.getDeltaMovement();
        Vec3 offset = new Vec3(
                dx + playerDelta.x + 0.005,
                dy + playerDelta.y - 0.002,
                dz + playerDelta.z + 0.005);

        String mode = this.targetEsp.getValue();
        switch (mode) {
            case "Spiral" -> RenderUtil.drawSpiralEffect(poseStack, entity, event.partialTick());
            case "Box" -> {
                int hurtTime = entity instanceof LivingEntity le ? le.hurtTime : 0;
                Color color;
                if (hurtTime == 0) {
                    color = new Color(0, 0, 0, 130);
                } else if (hurtTime >= 9 && hurtTime <= 10) {
                    color = new Color(0, 255, 255, 200);
                } else {
                    color = new Color(255, 0, 0, 200);
                }
                AABB base = EntityUtil.getInterpolatedAABB(entity, event.partialTick()).move(offset);
                AABB padded = new AABB(
                        base.minX - 0.175, base.minY - 0.125, base.minZ - 0.175,
                        base.maxX + 0.175, base.maxY + 0.225, base.maxZ + 0.175);
                RenderUtil.drawFilledColoredBox(padded, poseStack, color, color);
            }
            case "Tab" -> {
                int hurtTime = entity instanceof LivingEntity le ? le.hurtTime : 0;
                Color color;
                if (hurtTime == 0) {
                    color = new Color(0, 0, 0, 130);
                } else if (hurtTime == 3) {
                    color = new Color(255, 255, 255, 200);
                } else {
                    color = new Color(255, 0, 0, 200);
                }
                AABB base = EntityUtil.getInterpolatedAABB(entity, event.partialTick()).move(offset);
                AABB band = new AABB(
                        base.minX, base.minY + entity.getEyeHeight() + 0.11, base.minZ,
                        base.maxX, base.maxY - 0.13, base.maxZ);
                RenderUtil.drawFilledColoredBox(band, poseStack, color, color);
            }
            case "NurikZapen" -> this.renderNurikZapen(poseStack, entity, event.partialTick());
            default -> {
            }
        }
        poseStack.popPose();
    }

    private static void ensureNurikCaptureTexture() {
        if (nurikTextureLoaded || nurikTextureLoadFailed) {
            return;
        }
        try (InputStream inputStream = Assets.open(NURIK_CAPTURE_ASSET)) {
            if (inputStream == null) {
                nurikTextureLoadFailed = true;
                System.out.println("KillAura: NurikZapen texture not found - " + NURIK_CAPTURE_ASSET);
                return;
            }
            mc.getTextureManager().register(NURIK_CAPTURE_TEXTURE, new DynamicTexture(NativeImage.read(inputStream)));
            nurikTextureLoaded = true;
        } catch (IOException exception) {
            nurikTextureLoadFailed = true;
            System.out.println("KillAura: failed to load NurikZapen texture - " + exception.getMessage());
        }
    }

    private void renderNurikZapen(PoseStack poseStack, Entity entity, float partialTick) {
        ensureNurikCaptureTexture();
        if (!nurikTextureLoaded) {
            return;
        }

        double x = Mth.lerp(partialTick, entity.xOld, entity.getX());
        double y = Mth.lerp(partialTick, entity.yOld, entity.getY()) + entity.getEyeHeight() * 0.5f;
        double z = Mth.lerp(partialTick, entity.zOld, entity.getZ());
        Camera camera = mc.gameRenderer.getMainCamera();

        poseStack.pushPose();
        try {
            poseStack.translate(x, y, z);
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - camera.getYRot()));
            poseStack.mulPose(Axis.XP.rotationDegrees(-camera.getXRot()));
            poseStack.mulPose(Axis.ZP.rotationDegrees((float) ((System.currentTimeMillis() / 5.0) % 360.0)));

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableCull();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.setShaderTexture(0, NURIK_CAPTURE_TEXTURE);

            ModuleListHud moduleList = NightClient.getInstance().getHudManager().getHudElement(ModuleListHud.class);
            int[] colors = moduleList == null
                    ? new int[]{0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF}
                    : new int[]{
                            moduleList.getThemeColor(0, 0.0f, 3),
                            moduleList.getThemeColor(1, 0.33f, 3),
                            moduleList.getThemeColor(2, 0.67f, 3),
                            moduleList.getThemeColor(3, 1.0f, 3)
                    };
            float size = 0.75f;
            float[][] corners = {
                    {-size, size, 0.0f, 0.0f},
                    {size, size, 1.0f, 0.0f},
                    {size, -size, 1.0f, 1.0f},
                    {-size, -size, 0.0f, 1.0f}
            };
            Matrix4f matrix = poseStack.last().pose();
            BufferBuilder bufferBuilder = Tesselator.getInstance().getBuilder();
            bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            for (int i = 0; i < corners.length; i++) {
                int color = colors[i];
                bufferBuilder.vertex(matrix, corners[i][0], corners[i][1], 0.0f)
                        .uv(corners[i][2], corners[i][3])
                        .color((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, 200)
                        .endVertex();
            }
            BufferUploader.drawWithShader(bufferBuilder.end());
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            poseStack.popPose();
        }
    }

    @EventTarget
    public void onSprint(SprintEvent event) {
        if (this.keepSprint.getValue()) {
            ++this.sprintTickCounter;
            if (this.sprintTickCounter % 2 == 0 && mc.player != null) {
                mc.player.setSprinting(false);
            }
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!NightClient.isReady()) {
            return;
        }
        if (mc.screen instanceof AbstractContainerScreen
                || ItemUtil.hasServerItem()
                || (Scaffold.INSTANCE != null && Scaffold.INSTANCE.isEnabled())
                || (Stuck.INSTANCE != null && Stuck.INSTANCE.isEnabled())
                || (Helper.INSTANCE != null && Helper.INSTANCE.isEnabled() && Helper.targetRotation != null)
                || AntiWeb.targetRotation != null
                || AntiTNT.targetRotation != null
                || MidPearl.targetRotation != null
                || this.isWebPlacing()) {
            target = null;
            aimingTarget = null;
            this.currentBestHit = null;
            this.rotation = null;
            this.prevBestHit = null;
            targetList.clear();
            this.sprintTickCounter = 0;
            this.attacks = 0.0f;
            this.sprintCounter = 0;
            return;
        }
        boolean isSwitch = this.switchSize.getValue().intValue() > 1
                || this.infSwitch.getValue()
                || this.multiAttack.getValue();
        this.updateTargets();
        aimingTarget = this.getTarget();
        this.prevBestHit = this.currentBestHit;
        this.currentBestHit = null;
        if (aimingTarget != null) {
            this.currentBestHit = RotationUtil.getBestHit(aimingTarget);
            Rotation rawTarget = this.currentBestHit != null ? this.currentBestHit.rotation() : null;
            if (rawTarget != null) {
                Rotation from = RotationHandler.prevRotation != null
                        ? RotationHandler.prevRotation
                        : new Rotation(mc.player.getYRot(), mc.player.getXRot());
                Rotation organic = this.applyOrganicRotation(from, rawTarget, 1.0f);
                this.rotation = (organic != null
                        && !Float.isNaN(organic.getYaw())
                        && !Float.isNaN(organic.getPitch())
                        && !Float.isInfinite(organic.getYaw())
                        && !Float.isInfinite(organic.getPitch()))
                        ? organic
                        : rawTarget;
            } else {
                this.rotation = null;
            }
        } else {
            this.rotation = null;
        }
        if (targetList.isEmpty()) {
            target = null;
            return;
        }
        if (this.targetIndex > targetList.size() - 1) {
            this.targetIndex = 0;
        }
        if (targetList.size() > 1
                && (this.attackTimes >= this.switchDelay.getValue().intValue()
                || (this.currentBestHit != null && this.currentBestHit.distance() > 3.0))) {
            this.attackTimes = 0;
            for (int i = 0; i < targetList.size(); ++i) {
                ++this.targetIndex;
                if (this.targetIndex > targetList.size() - 1) {
                    this.targetIndex = 0;
                }
                Entity nextTarget = targetList.get(this.targetIndex);
                RotationUtil.BestHitInfo nextHit = RotationUtil.getBestHit(nextTarget);
                if (nextHit != null && nextHit.distance() < 3.0) {
                    break;
                }
            }
        }
        if (this.targetIndex > targetList.size() - 1 || !isSwitch) {
            this.targetIndex = 0;
        }
        target = targetList.get(this.targetIndex);
        if (this.delayMode.is("1.8")) {
            float apsValue;
            float minApsValue;
            if (NoXZMode.isAttacking) {
                int kbAttackAmount = AntiKB.INSTANCE != null
                        ? AntiKB.INSTANCE.attackAmount.getValue().intValue()
                        : 0;
                apsValue = this.maxAps.getValue().floatValue() - kbAttackAmount;
                minApsValue = this.minAps.getValue().floatValue() - kbAttackAmount;
            } else {
                apsValue = this.maxAps.getValue().floatValue();
                minApsValue = this.minAps.getValue().floatValue();
            }
            if (this.keepSprint.getValue()) {
                apsValue *= 2.0f;
                minApsValue *= 2.0f;
                // Vulcan limit: cap at 13 APS
                if (apsValue > 13.0f) apsValue = 13.0f;
                if (minApsValue > 13.0f) minApsValue = 13.0f;
            }
            if (this.cpsMode.is("Organic")) {
                this.attacks += this.getOrganicAttackIncrement(minApsValue, apsValue);
            } else {
                this.attacks += (float)(MathUtil.randomDouble(minApsValue, apsValue) / 20.0);
            }
        } else if (this.sprintCounter > 0) {
            this.sprintCounter--;
        } else if (mc.player.getAttackStrengthScale(0.0f) >= 0.9f) {
            this.doAttack();
        }
    }

    @EventTarget
    public void onPreMotion(PreMotionEvent event) {
        if (mc.player == null) return;
        if (this.isWebPlacing()) {
            this.attacks = 0.0f;
            return;
        }
        if (mc.player.getUseItem().isEmpty()
                && mc.screen == null
                && (this.ignoreSkipTicks.getValue() || ClientBase.delayPackets.isEmpty()
                || (Critical.INSTANCE != null && Critical.INSTANCE.isEnabled()))) {
            while (this.attacks >= 1.0f) {
                if (this.fix.getValue()) {
                    if (!this.doAttack()) {
                        break;
                    }
                } else {
                    this.doAttack();
                }
                this.attacks -= 1.0f;
            }
        } else {
            this.attacks = 0.0f;
        }
    }

    public boolean doAttack() {
        if (this.isWebPlacing()) {
            this.attacks = 0.0f;
            return false;
        }
        if (targetList.isEmpty()) return false;
        if (this.rotation == null) return false;

        HitResult hitResult;
        if (this.overrideRaycast.getValue() && this.rotation != null) {
            hitResult = RotationUtil.performRaycast(this.rotation);
            if (hitResult == null || hitResult.getType() != HitResult.Type.ENTITY) {
                return false;
            }
        } else {
            hitResult = mc.hitResult;
        }
        if (hitResult != null && hitResult.getType() == HitResult.Type.ENTITY) {
            Entity hitEntity = ((EntityHitResult) hitResult).getEntity();
            if (AntiBots.isBot(hitEntity)) {
                ChatUtil.print("Skipped attack on suspected bot");
                return false;
            }
        }
        if (this.multiAttack.getValue()) {
            int attacked = 0;
            Rotation aimRot = RotationHandler.targetRotation;
            for (Entity entity : targetList) {
                if (mc.player == null) break;
                if (aimRot != null && RotationUtil.getHitDistance(entity, mc.player.getEyePosition(), aimRot) >= 3.0) continue;
                if (this.attackEntity(entity)) {
                    attacked++;
                }
                if (attacked >= 2) break;
            }
            return attacked > 0;
        } else if (hitResult != null && hitResult.getType() == HitResult.Type.ENTITY) {
            Entity hitEntity = ((EntityHitResult) hitResult).getEntity();
            return this.attackEntity(hitEntity);
        } else if (target != null && targetList.contains(target)) {
            return this.attackEntity(target);
        }
        return false;
    }

    public Entity getTarget() {
        Entity entity = target;
        if (entity == null) {
            List<Entity> list = this.getTargets();
            if (!list.isEmpty()) {
                entity = list.get(0);
            }
        }
        if (entity != null) {
            AntiBots antiBots = AntiBots.INSTANCE;
            if (antiBots != null && antiBots.isEnabled() && AntiBots.isBot(entity)) {
                return null;
            }
        }
        return entity;
    }

    public void updateTargets() {
        List<Entity> next = this.getTargets();
        targetList = next != null ? next : new ArrayList<>();
    }

    public boolean isValidTarget(Entity entity) {
        if (!NightClient.isReady()) return false;
        if (entity == mc.player) return false;
        if (entity instanceof LivingEntity livingEntity) {
            AntiBots antiBots = AntiBots.INSTANCE;
            if (antiBots != null && antiBots.isEnabled() && (AntiBots.isBot(entity) || AntiBots.isBedWarsBot(entity))) {
                return false;
            }
            if (livingEntity.isDeadOrDying() || livingEntity.getHealth() <= 0.0f) return false;
            if (entity instanceof ArmorStand) return false;
            if (entity.isInvisible() && !(Boolean) this.attackInvisible.getValue()) return false;
            if (entity instanceof Player player) {
                if (this.test.getValue() && player.getY() >= mc.player.getY() + 0.05f) {
                    return true;
                }
                // NightClient.isOwner() was stripped during deobfuscation; the
                // original jar bailed here when the entity name matched the
                // client owner. Re-enable once that helper is restored.
            }
            if (Teams.isSameTeam(entity)) return false;
            if (entity instanceof Player && !(Boolean) this.attackPlayer.getValue()) return false;
            if (entity instanceof Player && (entity.getBbWidth() < 0.5 || livingEntity.isSleeping())) return false;
            if ((entity instanceof Mob || entity instanceof Slime || entity instanceof Bat || entity instanceof AbstractGolem)
                    && !(Boolean) this.attackMobs.getValue()) {
                return false;
            }
            if ((entity instanceof Animal || entity instanceof Squid) && !(Boolean) this.attackAnimals.getValue()) {
                return false;
            }
            if (entity instanceof Villager && !(Boolean) this.attackAnimals.getValue()) return false;
            return !(entity instanceof Player) || !entity.isSpectator();
        }
        return false;
    }

    public boolean isValidAttack(Entity entity) {
        if (mc.player == null) return false;
        if (!this.isValidTarget(entity)) return false;
        if (entity instanceof LivingEntity le && le.hurtTime > this.hurtTime.getValue().intValue()) {
            return false;
        }
        Vec3 vec3 = RotationUtil.closestPoint(mc.player.getEyePosition(), entity.getBoundingBox());
        double dist = vec3.distanceTo(mc.player.getEyePosition());
        float aimRange = this.aimRange.getValue().floatValue();
        if (dist <= aimRange) {
        } else if (dist > 5.0) {
            return false;
        } else {
            if (!(Boolean) this.predictionEnabled.getValue()
                    || this.predictDistance(entity) >= aimRange) {
                return false;
            }
        }
        // Wall check — reject if a block is between player and entity
        if (mc.level == null) return false;
        Vec3 eyePos = mc.player.getEyePosition(1.0f);
        Vec3 targetPoint = RotationUtil.closestPoint(eyePos, entity.getBoundingBox());
        // Skip wall check if player is inside the entity's bounding box
        if (eyePos.distanceToSqr(targetPoint) > 1.0E-4) {
            BlockHitResult blockHit = mc.level.clip(new ClipContext(eyePos, targetPoint, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
            if (blockHit.getType() == HitResult.Type.BLOCK) {
                return false;
            }
        }
        return RotationUtil.isEntityInFov(entity, this.fov.getValue().floatValue() / 2.0f);
    }

    private double predictDistance(Entity entity) {
        double selfDelayMs = 0.0;
        if (mc.getConnection() != null
                && mc.getConnection().getPlayerInfo(mc.player.getUUID()) != null) {
            selfDelayMs = mc.getConnection().getPlayerInfo(mc.player.getUUID()).getLatency();
        }
        double selfDelayTicks = Math.min(selfDelayMs / 50.0, this.selfDelayThreshold.getValue().doubleValue());

        double enemyDelayMs = 0.0;
        if (entity instanceof Player player) {
            if (mc.getConnection() != null
                    && mc.getConnection().getPlayerInfo(player.getUUID()) != null) {
                enemyDelayMs = mc.getConnection().getPlayerInfo(player.getUUID()).getLatency();
            }
        }
        double enemyDelayTicks = Math.min(enemyDelayMs / 50.0, this.enemyDelayThreshold.getValue().doubleValue());

        double totalTicks = 2.0 + selfDelayTicks + enemyDelayTicks;

        double playerVelX = mc.player.getX() - mc.player.xOld;
        double playerVelZ = mc.player.getZ() - mc.player.zOld;
        double enemyVelX = entity.getX() - entity.xOld;
        double enemyVelZ = entity.getZ() - entity.zOld;

        double predictedPlayerX = mc.player.getX() + playerVelX * totalTicks;
        double predictedPlayerZ = mc.player.getZ() + playerVelZ * totalTicks;
        double predictedEnemyX = entity.getX() + enemyVelX * totalTicks;
        double predictedEnemyZ = entity.getZ() + enemyVelZ * totalTicks;

        double dx = predictedEnemyX - predictedPlayerX;
        double dz = predictedEnemyZ - predictedPlayerZ;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public boolean attackEntity(Entity entity) {
        if (mc.player == null || mc.gameMode == null) return false;
        if (this.isWebPlacing()) return false;

        float currentYaw = mc.player.getYRot();
        float currentPitch = mc.player.getXRot();
        if (RotationHandler.targetRotation != null) {
            mc.player.setYRot(RotationHandler.targetRotation.getYaw());
            mc.player.setXRot(RotationHandler.targetRotation.getPitch());
        }

        boolean canAttackWithSprint = !this.keepSprint.getValue()
                || this.sprintTickCounter % 2 == 0;
        if (!canAttackWithSprint) {
            mc.player.setYRot(currentYaw);
            mc.player.setXRot(currentPitch);
            return false;
        }

        ++this.attackTimes;

        // Reach dynamic cap: randomly vary maxReach 2.90-3.08 per attack
        if (this.reachProtect.getValue()) {
            double effectiveReach = MathUtil.randomDouble(2.90, this.maxReach.getValue().doubleValue());
            if (mc.player.distanceTo(entity) > effectiveReach) {
                mc.player.setYRot(currentYaw);
                mc.player.setXRot(currentPitch);
                return false;
            }
        }

        int attackKey = mc.options.keyAttack.getKey().getValue();
        mc.gameMode.attack(mc.player, entity);
        ForgeHooksClient.onMouseButtonPre(attackKey, 1, 0);
        mc.player.swing(InteractionHand.MAIN_HAND);
        ForgeHooksClient.onMouseButtonPost(attackKey, 1, 0);

        if (this.morePart.getValue()) {
            mc.player.magicCrit(entity);
            mc.player.crit(entity);
        }

        mc.player.setYRot(currentYaw);
        mc.player.setXRot(currentPitch);

        if (this.delayMode.is("1.9")) {
            this.sprintCounter = (int) mc.player.getCurrentItemAttackStrengthDelay();
        }
        return true;
    }

    private boolean isWebPlacing() {
        return AutoWebPlace.INSTANCE != null && AutoWebPlace.INSTANCE.isEnabled() && AutoWebPlace.targetRotation != null;
    }

    private List<Entity> getTargets() {
        if (mc.player == null || mc.level == null) {
            return new ArrayList<>();
        }
        Stream<Entity> stream = StreamSupport.stream(mc.level.entitiesForRendering().spliterator(), true)
                .filter(this::isValidAttack);
        List<Entity> possibleTargets = stream.collect(Collectors.toList());
        if (this.priorityMode.is("Distance")) {
            possibleTargets.sort(Comparator.comparingDouble(KillAura::getDistanceToPlayer));
        } else if (this.priorityMode.is("FoV")) {
            possibleTargets.sort(Comparator.comparingDouble(KillAura::getAngleDiffToTarget));
        } else if (this.priorityMode.is("Health")) {
            possibleTargets.sort(Comparator.comparingDouble(KillAura::getEntityHealth));
        }
        if (this.preferBaby.getValue()
                && possibleTargets.stream().anyMatch(KillAura::isBaby)) {
            possibleTargets.removeIf(KillAura::isNotBaby);
        }
        possibleTargets.sort(Comparator.comparing(KillAura::getCrystalPriority));
        if (this.infSwitch.getValue()) {
            return possibleTargets;
        }
        int limit = (int) Math.min(possibleTargets.size(), this.switchSize.getValue().intValue());
        return new ArrayList<>(possibleTargets.subList(0, limit));
    }

    private static Integer getCrystalPriority(Entity entity) {
        return entity instanceof EndCrystal ? 0 : 1;
    }

    private static boolean isNotBaby(Entity entity) {
        return !(entity instanceof LivingEntity) || !((LivingEntity) entity).isBaby();
    }

    private static boolean isBaby(Entity entity) {
        return entity instanceof LivingEntity && ((LivingEntity) entity).isBaby();
    }

    private static double getEntityHealth(Entity entity) {
        if (entity instanceof LivingEntity le) {
            return le.getHealth();
        }
        return 0.0;
    }

    private static double getAngleDiffToTarget(Entity entity) {
        return RotationUtil.angleDiff(RotationHandler.targetRotation.getYaw(), RotationUtil.entityRotation(entity).getYaw());
    }

    private static double getDistanceToPlayer(Entity entity) {
        return entity.distanceTo(mc.player);
    }

    private static boolean isLivingEntity(Entity entity) {
        return entity instanceof LivingEntity;
    }

    /**
     * Organic CPS rhythm: cycles through 3 phases to create natural-feeling click timing.
     * Phase 0 (70% chance): normal speed minAps~maxAps
     * Phase 1 (25% chance): burst mode maxAps~(maxAps+2), capped at 13
     * Phase 2 (5% chance):  200-400ms pause, contributes 0 this tick
     */
    private float getOrganicAttackIncrement(float minAps, float maxAps) {
        this.cpsPhase++;
        if (this.cpsPhase > 100) this.cpsPhase = 0;

        int mod = this.cpsPhase % 100;
        // 70%: normal
        if (mod < 70) {
            return (float)(MathUtil.randomDouble(minAps, maxAps) / 20.0);
        }
        // 25%: burst (maxAps to maxAps+2, hard cap 13)
        if (mod < 95) {
            float burstMax = Math.min(maxAps + 2.0f, 13.0f);
            return (float)(MathUtil.randomDouble(maxAps, burstMax) / 20.0);
        }
        // 5%: pause (200-400ms = 4-8 ticks, but we add 0 to attacks counter each call)
        return 0.0f;
    }

    // --- Organic Block timing jitter (consumed by OldHitting renderer) ---

    /** Block duration: 70%-130% of base duration, randomized per cycle */
    public double getOrganicBlockDuration() {
        if (!this.organicBlock.getValue()) {
            return this.currentBlockDuration;
        }
        return this.currentBlockDuration * (0.7 + Math.random() * 0.6);
    }

    /** Pre-attack block delay: 30-100ms randomized */
    public long getPreBlockDelayNanos() {
        if (!this.organicBlock.getValue()) {
            return 0;
        }
        // Convert ms to nanoseconds
        return (long)((30 + Math.random() * 70) * 1_000_000L);
    }

    /** 5% chance to skip blocking for one attack cycle, but still attack */
    public boolean shouldSkipBlock() {
        if (!this.organicBlock.getValue()) {
            return false;
        }
        return Math.random() < 0.05;
    }
}
