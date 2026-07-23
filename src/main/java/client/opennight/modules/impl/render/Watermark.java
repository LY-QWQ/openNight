package client.opennight.modules.impl.render;

import client.opennight.NightClient;
import client.opennight.event.impl.GlRenderEvent;
import client.opennight.event.impl.Render2DEvent;
import client.opennight.hud.DynamicIsland;
import client.opennight.hud.LogoWatermark;
import client.opennight.hud.NeverloseWatermark;
import client.opennight.modules.Category;
import client.opennight.modules.Module;
import client.opennight.render.DrawContext;
import client.opennight.render.FontRenderer;
import client.opennight.render.FontPresets;
import client.opennight.render.Fonts;
import client.opennight.render.Paint;
import client.opennight.render.RoundedRectangle;
import client.opennight.settings.impl.BooleanSetting;
import client.opennight.settings.impl.ModeSetting;
import client.opennight.settings.impl.NumberSetting;
import client.opennight.event.EventTarget;
import client.opennight.utils.render.RenderUtil;
import net.minecraft.client.multiplayer.PlayerInfo;

public class Watermark extends Module {
    final ModeSetting styleSetting = new ModeSetting("Style", "Neverlose", "DynamicIsland", "Simple", "Pharos", "Logo").withDefault("DynamicIsland");
    private final NumberSetting bgAlpha = new NumberSetting("BG Alpha", 160, 0, 255, 1);
    private final BooleanSetting glow = new BooleanSetting("Glow", false);
    private final NumberSetting glowRadius = new NumberSetting("Glow Radius", 12, 4, 40, 1);
    private final NumberSetting glowAlpha = new NumberSetting("Glow Alpha", 120, 0, 255, 1);
    private final DynamicIsland dynamicIsland = new DynamicIsland();
    private final LogoWatermark logoWatermark = new LogoWatermark();
    private final NeverloseWatermark neverloseWatermark = new NeverloseWatermark();

    // Pharos style (original Simple - client name + fps)
    private static final FontRenderer titleFont = Fonts.getRenderer("quicksand.ttf", 36.0f);
    private static final FontRenderer fpsFont = Fonts.getRenderer("quicksand.ttf", 20.0f);
    private static final float MARGIN = 8.0f;

    // Simple capsule style (centered, like LiquidBounce Normal)
    private static final FontRenderer iconFont = FontPresets.nightIcon(24.0f);
    private static final FontRenderer textFont = FontPresets.pingfang(24.0f);
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int INFO_COLOR = 0xCCFFFFFF;
    private static final float ICON_SIZE = 20.0f;
    private static final float PAD = 3.0f;
    private static final int BG_COLOR_BASE = 0x0A0A0A;

    public Watermark() {
        super("Watermark", Category.RENDER);
    }

    @EventTarget
    public void onRender2D(Render2DEvent render2DEvent) {
        if (!this.isEnabled()) {
            return;
        }
        switch (this.styleSetting.getValue()) {
            case "Neverlose":
                this.neverloseWatermark.onRender2D(render2DEvent);
                break;
            case "DynamicIsland":
                this.dynamicIsland.onRender2D(render2DEvent);
                break;
        }
    }

    @EventTarget
    public void onGlRender(GlRenderEvent glRenderEvent) {
        if (!this.isEnabled()) {
            return;
        }
        DrawContext ctx = glRenderEvent.drawContext();
        switch (this.styleSetting.getValue()) {
            case "Neverlose":
                this.neverloseWatermark.onGlRender(glRenderEvent);
                break;
            case "Pharos":
                this.renderPharos(ctx);
                break;
            case "Simple":
                this.renderSimple(ctx);
                break;
            case "Logo":
                this.logoWatermark.onGlRender(glRenderEvent);
                break;
        }
    }

    private void renderSimple(DrawContext ctx) {
        if (mc.player == null) return;

        String icon = "N";
        String clientName = NightClient.CLIENT_NAME;
        String userName = mc.player.getGameProfile().getName();
        String fpsText = mc.getFps() + "fps";
        String pingText = getPing() + "ms";
        String infoText = " | " + userName + " | " + fpsText + " | " + pingText;

        float iconW = iconFont.getWidth(icon);
        float nameW = textFont.getWidth(clientName);
        float infoW = textFont.getWidth(infoText);

        float contentW = iconW + PAD + nameW + infoW;
        float lineH = Math.max(ICON_SIZE, textFont.getMetrics().height());
        float boxH = lineH + PAD * 2;
        float boxW = contentW + PAD * 2 + 6f;

        float screenW = mc.getWindow().getGuiScaledWidth();
        float screenH = mc.getWindow().getGuiScaledHeight();
        float x = (screenW - boxW) / 2;
        float y = screenH / 20;

        // Capsule background (full pill shape)
        float radius = boxH / 2;
        int bgColor = BG_COLOR_BASE | (bgAlpha.getValue().intValue() << 24);
        RoundedRectangle capsule = RoundedRectangle.ofXYWHR(x, y, boxW, boxH, radius);
        ctx.drawRoundedRect(capsule, new Paint().setColor(bgColor));

        // Glow behind background (like Armor module)
        if (glow.getValue()) {
            int gRadius = this.glowRadius.getValue().intValue();
            int gAlpha = this.glowAlpha.getValue().intValue();
            if (gAlpha > 0 && gRadius > 0) {
                RenderUtil.drawShadow(ctx.getPoseStack(), x, y, boxW, boxH, gRadius, (gAlpha << 24) | 0x000000);
                RenderUtil.enableBlend();
            }
        }

        float cursorX = x + PAD + 3f;
        float textY = y + PAD + (lineH - iconFont.getMetrics().height()) / 2 + 13f;

        // Icon "N"
        ctx.drawString(icon, cursorX, textY, iconFont, new Paint().setColor(TEXT_COLOR));
        cursorX += iconW + PAD;

        // Client name "Night"
        float nameY = y + PAD + (lineH - textFont.getMetrics().height()) / 2 + 10f;
        ctx.drawString(clientName, cursorX, nameY, textFont, new Paint().setColor(TEXT_COLOR));
        cursorX += nameW;

        // Info text (username | fps | ping) in dimmer white
        float infoY = y + PAD + (lineH - textFont.getMetrics().height()) / 2 + 10f;
        ctx.drawString(infoText, cursorX, infoY, textFont, new Paint().setColor(INFO_COLOR));
    }

    private void renderPharos(DrawContext ctx) {
        if (mc.player == null) return;

        Paint textPaint = new Paint().setColor(0xFFFFFFFF);
        Paint fpsPaint = new Paint().setColor(0xFFFFFFFB);

        float padX = 6.0f;
        float padY = 4.0f;
        float baseX = MARGIN - 5f;
        float baseY = MARGIN;

        float titleX = baseX + padX;
        float titleY = baseY + padY + titleFont.getMetrics().capHeight();
        ctx.drawString(NightClient.CLIENT_NAME, titleX, titleY, titleFont, textPaint);

        String fpsStr = String.valueOf(mc.getFps());
        float fpsX = baseX + padX;
        float fpsY = titleY + titleFont.getMetrics().capHeight() - 7.0f + fpsFont.getMetrics().capHeight();
        ctx.drawString(fpsStr, fpsX, fpsY, fpsFont, fpsPaint);
    }

    private int getPing() {
        if (mc.player == null || mc.player.connection == null) return 0;
        PlayerInfo playerInfo = mc.player.connection.getPlayerInfo(mc.player.getUUID());
        return playerInfo != null ? playerInfo.getLatency() : 0;
    }
}
