package client.nilore.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.Mth;
import client.nilore.event.EventTarget;
import client.nilore.event.impl.GlRenderEvent;
import client.nilore.event.impl.ModuleToggleEvent;
import client.nilore.event.impl.Render2DEvent;
import client.nilore.render.DrawContext;
import client.nilore.render.FontPresets;
import client.nilore.render.FontRenderer;
import client.nilore.render.GlHelper;
import client.nilore.render.Paint;
import client.nilore.render.Renderer;
import client.nilore.render.RoundedRectangle;
import client.nilore.settings.impl.NumberSetting;
import client.nilore.utils.math.LerpUtil;
import client.nilore.utils.misc.SoundUtil;
import client.nilore.utils.render.ColorUtil;
import client.nilore.utils.render.TextureUtil;

public class NotificationHud extends HudElement {

    private static final float CARD_WIDTH = 171.0f;
    private static final float CARD_HEIGHT = 45.0f;
    private static final float CARD_RADIUS = 5.4f;
    private static final float PADDING = 7.2f;
    private static final float BAR_HEIGHT = 2.7f;
    private static final float SPACING = 5.4f;
    private static final float ICON_SIZE = 21.6f;
    private static final int BG_COLOR = 0xFF111615;
    private static final int BAR_COLOR = 0xFFFFFFFF;
    private static final int BAR_BG_COLOR = 0xFF3A3A3A;
    private static final int TEXT_COLOR = 0xFFFFFFFF;

    private final NumberSetting margin = new NumberSetting("Margin", 8.0f, 0.0f, 100.0f, 1.0f);
    private final NumberSetting duration = new NumberSetting("Duration (ms)", 900, 500, 10000, 100);
    private final NumberSetting maxNotifications = new NumberSetting("Max Notifications", 7, 1, 10, 1);

    private final List<NotificationEntry> notifications = new ArrayList<>();

    private DynamicTexture enabledIcon;
    private DynamicTexture disabledIcon;

    public NotificationHud() {
        super("Notification");
        this.setWidth(CARD_WIDTH);
        this.setHeight(CARD_HEIGHT);
        this.setEnabled(true);
    }

    @Override
    public void registerSettings() {
        this.registerSetting(margin, duration, maxNotifications);
    }

    @EventTarget
    public void onModuleToggle(ModuleToggleEvent event) {
        if (event.module() == this) {
            return;
        }
        loadTextures();
        notifications.add(new NotificationEntry(
                event.module().getName(),
                event.enabled(),
                System.currentTimeMillis()
        ));
        while (notifications.size() > maxNotifications.getValue().intValue()) {
            notifications.remove(0);
        }
        // 播放开关提示音（只在 NotificationHud 启用时播放）
        if (this.isEnabled()) {
            String soundName = event.enabled() ? "Enabled.wav" : "Disabled.wav";
            SoundUtil.playSound(soundName, 0.0f);
        }
    }

    @Override
    public void onRender2D(Render2DEvent event, float px, float py) {
        if (mc.getWindow() == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long dur = duration.getValue().longValue();
        float screenW = mc.getWindow().getGuiScaledWidth();
        float screenH = mc.getWindow().getGuiScaledHeight();
        float marginVal = margin.getValue().floatValue();
        float targetX = screenW - CARD_WIDTH - marginVal;
        float baseY = screenH - CARD_HEIGHT - marginVal;

        Iterator<NotificationEntry> it = notifications.iterator();
        while (it.hasNext()) {
            NotificationEntry entry = it.next();
            long elapsed = now - entry.time;

            if (elapsed < dur) {
                // Visible: entrance or steady state
                if (!entry.entranceStarted) {
                    entry.entranceStarted = true;
                    entry.entranceTime = now;
                    entry.x = screenW + 10.0f;
                }
                float entranceElapsed = (now - entry.entranceTime) / 1000f;
                float slideDuration = 0.08f;
                float t = Math.min(1.0f, entranceElapsed / slideDuration);
                entry.x = (screenW + 10.0f) + (targetX - (screenW + 10.0f)) * t;
                entry.alpha = Math.min(1.0f, entry.alpha + 0.05f);
            } else if (!entry.exiting) {
                // Time's up: start exit
                entry.exiting = true;
                entry.lastBarProgress = 0.0f;
                entry.exitStartTime = now;
            } else {
                // Exit — time-based linear, fast 0.04s
                float exitElapsed = (now - entry.exitStartTime) / 1000f;
                float exitDuration = 0.04f;
                float t = Math.min(1.0f, exitElapsed / exitDuration);
                entry.x = targetX + ((screenW + 10.0f) - targetX) * t;
                entry.alpha = 1.0f - t;
                if (t >= 1.0f) {
                    it.remove();
                    continue;
                }
            }
        }

        if (notifications.isEmpty()) {
            return;
        }

        Renderer.render(event.guiGraphics(), drawContext -> {
            for (int i = 0; i < notifications.size(); i++) {
                NotificationEntry entry = notifications.get(i);
                long elapsed = now - entry.time;
                float fadeAlpha = Mth.clamp(entry.alpha, 0.0f, 1.0f);
                float cardX = entry.x;
                float cardY = baseY - i * (CARD_HEIGHT + SPACING);

                float progress;
                if (!entry.exiting) {
                    progress = 1.0f - Mth.clamp((float) elapsed / dur, 0.0f, 1.0f);
                    entry.lastBarProgress = progress;
                } else {
                    progress = entry.lastBarProgress;
                }

                renderCard(drawContext, entry, cardX, cardY, progress, fadeAlpha);
            }
        });
    }

    private void renderCard(DrawContext drawContext, NotificationEntry entry,
                            float x, float y, float progress, float alpha) {
        RoundedRectangle rect = RoundedRectangle.ofXYWHRadii(x, y, CARD_WIDTH, CARD_HEIGHT - 2f, new float[]{CARD_RADIUS, CARD_RADIUS, 0, 0});

        // Background
        try (Paint paint = new Paint()) {
            paint.setColor(ColorUtil.withAlpha(BG_COLOR, alpha));
            drawContext.drawRoundedRect(rect, paint);
        }

        // Progress bar background track - bottom aligned with card bottom, sharing corner radius
        float barBottom = y + CARD_HEIGHT;
        float barY = barBottom - BAR_HEIGHT;
        try (Paint paint = new Paint()) {
            paint.setColor(ColorUtil.withAlpha(BAR_BG_COLOR, alpha));
            drawContext.drawRoundedRect(
                    RoundedRectangle.ofXYWHRadii(x, barY, CARD_WIDTH, BAR_HEIGHT, new float[]{0, 0, CARD_RADIUS, CARD_RADIUS}),
                    paint
            );
        }

        // Progress bar
        float barWidth = CARD_WIDTH * progress;
        if (barWidth > 0.5f) {
            try (Paint paint = new Paint()) {
                paint.setColor(ColorUtil.withAlpha(BAR_COLOR, alpha));
                drawContext.drawRoundedRect(
                        RoundedRectangle.ofXYWHRadii(x, barY, barWidth, BAR_HEIGHT, new float[]{0, 0, CARD_RADIUS, CARD_RADIUS}),
                        paint
                );
            }
        }

        // Icon (pure white, original PNG colors preserved)
        DynamicTexture icon = entry.enabled ? enabledIcon : disabledIcon;
        float textOffsetX = PADDING;
        if (icon != null) {
            float drawSize = entry.enabled ? ICON_SIZE * 0.9f : ICON_SIZE;
            float iconX = x + PADDING;
            float iconY = y + (CARD_HEIGHT - BAR_HEIGHT - drawSize) / 2.0f;
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderTexture(0, icon.getId());
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            Matrix4f pose = drawContext.getPoseStack().last().pose();
            Tesselator tesselator = Tesselator.getInstance();
            BufferBuilder bufferBuilder = tesselator.getBuilder();
            bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            bufferBuilder.vertex(pose, iconX, iconY, 0.0f).uv(0.0f, 0.0f).endVertex();
            bufferBuilder.vertex(pose, iconX, iconY + drawSize, 0.0f).uv(0.0f, 1.0f).endVertex();
            bufferBuilder.vertex(pose, iconX + drawSize, iconY + drawSize, 0.0f).uv(1.0f, 1.0f).endVertex();
            bufferBuilder.vertex(pose, iconX + drawSize, iconY, 0.0f).uv(1.0f, 0.0f).endVertex();
            BufferUploader.drawWithShader(bufferBuilder.end());
            textOffsetX = PADDING + ICON_SIZE + 5.4f;
        }

        // Title text
        FontRenderer titleFont = FontPresets.pingfang(16.2f);
        FontRenderer descFont = FontPresets.pingfang(12.6f);

        float textX = x + textOffsetX;
        float titleY = y + PADDING + 6.05f;
        float descY = titleY + 12.2f;

        int titleColor = ColorUtil.withAlpha(TEXT_COLOR, alpha);
        int descColor = ColorUtil.withAlpha(0xFFCCCCCC, alpha);

        GlHelper.drawText("Module", textX, titleY, titleFont, titleColor);

        String stateText = "Toggled " + entry.name + " " + (entry.enabled ? "on" : "off");
        GlHelper.drawText(stateText, textX, descY, descFont, descColor);
    }

    @Override
    public void onGlRender(GlRenderEvent event, float x, float y) {
    }

    @Override
    public void onSettings() {
    }

    private void loadTextures() {
        if (enabledIcon != null && disabledIcon != null) {
            return;
        }
        enabledIcon = TextureUtil.loadTexture("Enabled.png");
        disabledIcon = TextureUtil.loadTexture("Disabled.png");
    }

    private static class NotificationEntry {
        final String name;
        final boolean enabled;
        final long time;
        float x;
        float alpha;
        boolean entranceStarted;
        long entranceTime;
        boolean exiting;
        long exitStartTime;
        float lastBarProgress = 1.0f;

        NotificationEntry(String name, boolean enabled, long time) {
            this.name = name;
            this.enabled = enabled;
            this.time = time;
            this.x = 9999f;
            this.alpha = 0f;
        }
    }
}
