package client.nilore.modules.impl.combat.antikb;

import java.util.concurrent.LinkedBlockingDeque;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import client.nilore.NiloreClient;
import client.nilore.event.impl.DisconnectEvent;
import client.nilore.event.impl.GameTickEvent;
import client.nilore.event.impl.MotionEvent;
import client.nilore.event.impl.PreMotionEvent;
import client.nilore.event.impl.ReceivePacketEvent;
import client.nilore.event.impl.RotationEvent;
import client.nilore.event.impl.SprintEvent;
import client.nilore.event.impl.StrafeEvent;
import client.nilore.event.impl.TickEvent;
import client.nilore.modules.impl.combat.AntiKB;
import client.nilore.modules.impl.combat.KillAura;
import client.nilore.modules.impl.combat.antikb.AntiKBMode;
import client.nilore.modules.impl.player.Stuck;
import client.nilore.utils.game.RayTraceUtil;
import client.nilore.utils.misc.ChatUtil;
import client.nilore.utils.rotation.Rotation;
import client.nilore.utils.rotation.RotationHandler;

public class NoXZMode
extends AntiKBMode {
    public static NoXZMode INSTANCE;
    public static boolean isAttacking;
    public static int attackCount;
    private int attackCooldown = 0;
    private Entity attackTarget = null;
    private Entity pendingTarget = null;
    private int attacksRemaining = 0;
    private int flagCooldown = 0;
    private boolean shouldJump = false;
    private int sprintBoostCounter = 0;
    private int hitCounter = 0;
    private boolean isSuspending = false;
    private int suspendTicks = 0;
    private ClientboundSetEntityMotionPacket knockbackPacket = null;
    private final LinkedBlockingDeque<Packet<?>> packetQueue = new LinkedBlockingDeque();
    private final LinkedBlockingDeque<Packet<?>> movePacketQueue = new LinkedBlockingDeque();
    private volatile boolean isFlushing = false;
    private float instantAttackProgress = 0.0f;
    private boolean isInstantAttacking = false;
    private boolean shouldFlushMotion;

    private void log(String message) {
        if (AntiKB.INSTANCE.log.getValue()) {
            ChatUtil.print(message);
        }
    }

    @Override
    public boolean isActive() {
        return this.isSuspending;
    }

    public NoXZMode() {
        super("NoXZ");
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        this.resetAll();
    }

    @Override
    public void onDisable() {
        this.resetAll();
    }

    @Override
    public String getName() {
        return "";
    }

    @Override
    public void onRotation(RotationEvent rotationEvent) {
    }

    @Override
    public void onMotion(MotionEvent motionEvent) {
        if (motionEvent.isPre() && this.shouldFlushMotion) {
            while (!this.packetQueue.isEmpty()) {
                Packet packet = this.packetQueue.poll();
                if (packet == null) continue;
                try {
                    packet.handle(mc.getConnection());
                } catch (Exception exception) {
                    exception.printStackTrace();
                }
            }
            this.shouldFlushMotion = false;
        }
    }

    @Override
    public void onReceivePacket(ReceivePacketEvent receivePacketEvent) {
        if (mc.player == null) {
            return;
        }
        if (this.isFlushing) {
            return;
        }
        if (this.shouldIgnore()) {
            return;
        }
        Packet<?> packet = receivePacketEvent.getPacket();
        if (packet instanceof ServerboundMovePlayerPacket && this.isSuspending) {
            this.movePacketQueue.add(packet);
            receivePacketEvent.setCancelled(true);
            return;
        }
        if (packet instanceof ClientboundPlayerPositionPacket) {
            if (this.isSuspending) {
                this.release();
            }
            this.resetSuspension();
            log("Flag Detected");
            this.flagCooldown = 2;
        }
        if (this.flagCooldown != 0) {
            return;
        }
        if (this.isSuspending) {
            if (!this.isAllowedPacket(packet)) {
                this.packetQueue.add(packet);
                receivePacketEvent.setCancelled(true);
            }
            return;
        }
        if (packet instanceof ClientboundSetEntityMotionPacket motionPacket) {
            if (motionPacket.getId() != mc.player.getId()) {
                return;
            }
            double dx = -motionPacket.getXa();
            double dz = -motionPacket.getZa();
            if (Math.abs(dx) > 0.01 || Math.abs(dz) > 0.01) {
                this.hitCounter = 1;
            }
            if (motionPacket.getYa() > 0) {
                Entity target;
                this.sprintBoostCounter = this.sprintBoostCounter % 100 + 100;
                if (this.sprintBoostCounter >= 100) {
                    this.shouldJump = true;
                }
                boolean canAttack = this.isValidTarget(target = this.getAttackTarget()) && mc.player.isSprinting();
                if (!mc.player.onGround()) {
                    this.isSuspending = true;
                    this.suspendTicks = 0;
                    this.knockbackPacket = motionPacket;
                    receivePacketEvent.setCancelled(true);
                } else if (canAttack) {
                    this.pendingTarget = target;
                    this.attacksRemaining = AntiKB.INSTANCE.attackAmount.getValue().intValue();
                } else {
                    this.isSuspending = true;
                    this.suspendTicks = 0;
                    this.knockbackPacket = motionPacket;
                    receivePacketEvent.setCancelled(true);
                    log("Alink Wait");
                }
            }
        }
    }

    @Override
    public void onDisconnect(DisconnectEvent disconnectEvent) {
        this.resetAll();
    }

    @Override
    public void onPreMotion(PreMotionEvent preMotionEvent) {
    }

    @Override
    public void onGameTick(GameTickEvent gameTickEvent) {
    }

    @Override
    public void onSprint(SprintEvent sprintEvent) {
    }

    private void resetAll() {
        this.clearTarget();
        this.flagCooldown = 0;
        this.shouldJump = false;
        this.sprintBoostCounter = 0;
        this.hitCounter = 0;
        this.resetSuspension();
    }

    private void clearTarget() {
        this.attackTarget = null;
        this.pendingTarget = null;
        this.attacksRemaining = 0;
    }

    private void resetSuspension() {
        this.isSuspending = false;
        this.suspendTicks = 0;
        this.knockbackPacket = null;
        this.packetQueue.clear();
        this.movePacketQueue.clear();
        this.isFlushing = false;
        this.instantAttackProgress = 0.0f;
        this.isInstantAttacking = false;
        NiloreClient.serverTickRate = 1.0f;
    }

    private boolean shouldIgnore() {
        if (mc.player == null || mc.level == null) {
            return true;
        }
        if (mc.player.isDeadOrDying() || !mc.player.isAlive() || mc.player.getHealth() <= 0.0f) {
            return true;
        }
        if (mc.player.isSpectator() || mc.player.getAbilities().flying) {
            return true;
        }
        if (mc.player.isInLava() || mc.player.isOnFire() || mc.player.isInWater() || mc.player.onClimbable() || mc.player.isSleeping()) {
            return true;
        }
        if (mc.level.getBlockState(mc.player.blockPosition()).is(Blocks.COBWEB)) {
            return true;
        }
        Stuck stuck = Stuck.INSTANCE;
        return stuck != null && stuck.isEnabled();
    }

    private double getAABBDistance(Entity entity) {
        if (mc.player == null) {
            return Double.MAX_VALUE;
        }
        Vec3 eyePos = mc.player.getEyePosition(1.0f);
        AABB box = entity.getBoundingBox();
        double clampedX = Math.max(box.minX, Math.min(eyePos.x, box.maxX));
        double clampedY = Math.max(box.minY, Math.min(eyePos.y, box.maxY));
        double clampedZ = Math.max(box.minZ, Math.min(eyePos.z, box.maxZ));
        return eyePos.distanceTo(new Vec3(clampedX, clampedY, clampedZ));
    }

    private Entity getHitResultEntity() {
        Entity hitEntity;
        if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.ENTITY && (hitEntity = ((EntityHitResult)mc.hitResult).getEntity()) instanceof LivingEntity && hitEntity != mc.player && hitEntity.isAlive() && !hitEntity.isSpectator()) {
            return hitEntity;
        }
        return null;
    }

    private Entity getAttackTarget() {
        if (KillAura.target != null) {
            return KillAura.target;
        }
        return this.getHitResultEntity();
    }

    private boolean isValidTarget(Entity entity) {
        LivingEntity livingEntity;
        if (entity == null || !entity.isAlive()) {
            return false;
        }
        if (entity instanceof LivingEntity && ((livingEntity = (LivingEntity)entity).isDeadOrDying() || livingEntity.getHealth() <= 0.0f)) {
            return false;
        }
        if (this.getAABBDistance(entity) > AntiKB.INSTANCE.maxReach.getValue().doubleValue()){
            log("Out of Reach");
            return false;
        }
        //增加了一个RayTrace
        if (AntiKB.INSTANCE.raytraceCheck.getValue()){
            Rotation rotation = new Rotation(RotationHandler.sentRotation.getYaw(), RotationHandler.sentRotation.getPitch());
            float inflate = 0.0f;
            boolean ignoreBlocks = false;
            HitResult hitResult = RayTraceUtil.rayTraceForEntity(
                    rotation,
                    AntiKB.INSTANCE.maxReach.getValue().doubleValue(),
                    inflate,
                    mc.player,
                    entity,
                    ignoreBlocks
            );
            if (!(hitResult instanceof EntityHitResult) ||
                    ((EntityHitResult) hitResult).getEntity() != entity) {
                log("Raytrace failed");
                return false;
            }
        }
        return true;
    }

    @Override
    public void onTick(TickEvent tickEvent) {
        if (mc.player == null) {
            return;
        }
        if (this.attackCooldown > 0) {
            --this.attackCooldown;
            if (this.attackCooldown <= 0) {
                isAttacking = false;
                attackCount = 0;
            }
        }
        if (this.hitCounter > 0) {
            ++this.hitCounter;
            if (this.hitCounter > 2) {
                this.hitCounter = 0;
            }
        }
        if (mc.player.isDeadOrDying() || !mc.player.isAlive() || this.shouldIgnore()) {
            this.clearTarget();
            if (this.isSuspending) {
                this.release();
            }
            if (this.isInstantAttacking) {
                this.isInstantAttacking = false;
                this.instantAttackProgress = 0.0f;
                NiloreClient.serverTickRate = 1.0f;
            }
            return;
        }
        if (this.flagCooldown > 0) {
            --this.flagCooldown;
            this.clearTarget();
        }
        if (this.isSuspending) {
            ++this.suspendTicks;
            boolean instantAttackEnabled = AntiKB.INSTANCE.instantAttack.getValue();
            if (instantAttackEnabled && this.instantAttackProgress < 3.0f) {
                float tickRate;
                NiloreClient.serverTickRate = tickRate = 0.5f;
                this.instantAttackProgress += 1.0f - tickRate;
                this.instantAttackProgress = Math.min(this.instantAttackProgress, 3.0f);
            }
            boolean onGround = mc.player.onGround();
            boolean isTimeout = this.suspendTicks >= 12;
            if (onGround || isTimeout) {
                log(isTimeout ? "Alink Timeout" : "ground");
                if (instantAttackEnabled) {
                    NiloreClient.serverTickRate = 1.0f;
                }
                Entity target = this.getAttackTarget();
                boolean canAttack = this.isValidTarget(target);
                boolean sprinting = mc.player.isSprinting();
                if (onGround && canAttack && sprinting) {
                    this.isFlushing = true;
                    this.pendingTarget = target;
                    this.attacksRemaining = AntiKB.INSTANCE.attackAmount.getValue().intValue();
                    this.sendMovePackets();
                    this.applyKnockbackPacket();
                    if (instantAttackEnabled && this.instantAttackProgress > 0.0f) {
                        this.attacksRemaining = (int)this.instantAttackProgress;
                        this.scheduleMotionFlush();

                        if (this.pendingTarget != null && this.isValidTarget(this.pendingTarget)) {
                            this.attackTarget = this.pendingTarget;
                        }
                        this.pendingTarget = null;

                        this.isSuspending = false;
                        this.suspendTicks = 0;
                        this.isFlushing = false;
                        this.isInstantAttacking = true;
                        NiloreClient.serverTickRate = 4.0f;
                    } else {
                        if (this.pendingTarget != null && this.isValidTarget(this.pendingTarget)) {
                            this.attackTarget = this.pendingTarget;
                        }
                        this.pendingTarget = null;

                        this.doAttackSequence(tickEvent);
                        this.scheduleMotionFlush();
                        this.isSuspending = false;
                        this.suspendTicks = 0;
                        this.isFlushing = false;
                    }
                } else {
                    this.release();
                    if (instantAttackEnabled) {
                        this.instantAttackProgress = 0.0f;
                    }
                    if (onGround && mc.player.isSprinting()) {
                        mc.player.setSprinting(false);
                    }
                }
                return;
            }
            return;
        }
        if (this.isInstantAttacking) {
            this.instantAttackProgress -= 1.0f;
            if (this.instantAttackProgress <= 0.0f) {
                this.instantAttackProgress = 0.0f;
                this.isInstantAttacking = false;
                NiloreClient.serverTickRate = 1.0f;
                log("done");
            }
        }
        if (this.attacksRemaining > 0 && this.attackTarget != null) {
            this.doAttackSequence(tickEvent);
        }
    }

    @Override
    public void onStrafe(StrafeEvent strafeEvent) {
        if (mc.player == null) {
            return;
        }
        if (this.hitCounter > 0) {
            strafeEvent.setForward(1.0f);
        }
        if (this.shouldJump) {
            this.shouldJump = false;
            if (mc.player.onGround() && mc.player.isSprinting() && !mc.player.hasEffect(MobEffects.JUMP) && !this.shouldIgnore()) {
                strafeEvent.setSprinting(true);
            }
        }
    }

    private void doAttackSequence(TickEvent tickEvent) {
        Entity aimed = this.getHitResultEntity();

        // 增加了 aimed == null 以及 aimed != this.attackTarget 的检验。
        // 如果视线中没有指向实体，或者指向的实体不是当前要攻击的 target，直接清空目标并取消攻击。
        if (this.attackTarget == null
                || !this.attackTarget.isAlive()
                || aimed == null
                || aimed != this.attackTarget) {
            log("aim point mismatch");
            this.clearTarget();
            return;
        }
        if (this.getAABBDistance(this.attackTarget) > AntiKB.INSTANCE.maxReach.getValue().doubleValue()) {
            log("Out of Reach");
            this.clearTarget();
            return;
        }
        if (AntiKB.INSTANCE.raytraceCheck.getValue()){
            Rotation rotation = new Rotation(RotationHandler.sentRotation.getYaw(), RotationHandler.sentRotation.getPitch());
            float inflate = 0.0f;
            boolean ignoreBlocks = false;
            HitResult hitResult = RayTraceUtil.rayTraceForEntity(
                    rotation,
                    AntiKB.INSTANCE.maxReach.getValue().doubleValue(),
                    inflate,
                    mc.player,
                    this.attackTarget,
                    ignoreBlocks
            );
            if (!(hitResult instanceof EntityHitResult) ||
                    ((EntityHitResult) hitResult).getEntity() != this.attackTarget) {
                log("Raytrace failed");
                this.clearTarget();
                return;
            }
        }
        isAttacking = true;
        attackCount = this.attacksRemaining--;
        this.attackCooldown = 2;
        this.doAttack(this.attackTarget);
        if (this.attacksRemaining <= 0) {
            this.clearTarget();
            if (AntiKB.INSTANCE.instantAttack.getValue()) {
                log("Attack (" + AntiKB.INSTANCE.attackAmount.getValue().intValue() + ")");
            }
        }
    }

    private boolean doAttack(Entity entity) {
        if (mc.player == null || mc.gameMode == null) {
            return false;
        }
        if (AntiKB.INSTANCE.sprintStateCheck.getValue() && !mc.player.isSprinting()) {
            log("not sprinting");
            return false;
        }
        boolean wasSprinting = mc.player.isSprinting();
        if (wasSprinting) {
            mc.player.setSprinting(false);
        }
        mc.gameMode.attack(mc.player, entity);
        mc.player.swing(InteractionHand.MAIN_HAND);
        if (wasSprinting) {
            Vec3 velocity = mc.player.getDeltaMovement();
            mc.player.setDeltaMovement(velocity.x * 0.6, velocity.y, velocity.z * 0.6);
        }
        if (!AntiKB.INSTANCE.instantAttack.getValue()) {
            log("Attack (" + this.attacksRemaining + ")");
        }
        return true;
    }

    private void sendMovePackets() {
        if (mc.getConnection() == null) {
            return;
        }
        while (!this.movePacketQueue.isEmpty()) {
            Packet packet = this.movePacketQueue.poll();
            if (packet == null) continue;
            try {
                mc.getConnection().send(packet);
            } catch (Exception exception) {
                exception.printStackTrace();
            }
        }
    }

    private void applyKnockbackPacket() {
        if (this.knockbackPacket != null && mc.getConnection() != null) {
            try {
                this.knockbackPacket.handle(mc.getConnection());
            } catch (Exception exception) {
                exception.printStackTrace();
            }
            this.knockbackPacket = null;
        }
    }

    private void scheduleMotionFlush() {
        if (mc.getConnection() == null) {
            return;
        }
        this.shouldFlushMotion = true;
    }

    private boolean isAllowedPacket(Packet<?> packet) {
        return packet instanceof ClientboundSetEntityMotionPacket || packet instanceof ClientboundSetHealthPacket || packet instanceof ClientboundPlayerPositionPacket || packet instanceof ClientboundSoundPacket || packet instanceof ClientboundPlayerChatPacket || packet instanceof ClientboundPlayerCombatKillPacket || packet instanceof ClientboundContainerClosePacket || packet instanceof ClientboundHurtAnimationPacket || packet instanceof ClientboundSetTitleTextPacket || packet instanceof ClientboundSetPlayerTeamPacket || packet instanceof ClientboundSystemChatPacket || packet instanceof ClientboundDisconnectPacket || packet instanceof ClientboundAnimatePacket && ((ClientboundAnimatePacket)packet).getId() != mc.player.getId();
    }

    private void release() {
        this.isFlushing = true;
        this.sendMovePackets();
        this.applyKnockbackPacket();
        this.scheduleMotionFlush();

        if (this.pendingTarget != null && this.isValidTarget(this.pendingTarget)) {
            this.attackTarget = this.pendingTarget;
        }
        this.pendingTarget = null;

        this.isFlushing = false;
        this.isSuspending = false;
        this.suspendTicks = 0;
        this.instantAttackProgress = 0.0f;
        this.isInstantAttacking = false;
        NiloreClient.serverTickRate = 1.0f;
    }

    static {
        isAttacking = false;
        attackCount = 0;
    }
}