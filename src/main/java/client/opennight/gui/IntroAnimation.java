package client.opennight.gui;

import java.awt.Color;
import client.opennight.ClientBase;
import client.opennight.NightClient;
import client.opennight.event.impl.GlRenderEvent;
import client.opennight.render.FontPresets;
import client.opennight.render.FontRenderer;
import client.opennight.render.GlHelper;
import client.opennight.render.Paint;
import client.opennight.event.EventTarget;
import client.opennight.NightClient;

public class IntroAnimation
extends ClientBase {
    private static volatile boolean isActive = false;
    private long startTime = -1L;
    private boolean finished = false;

    public IntroAnimation() {
        isActive = true;
    }

    public static boolean isRunning() {
        return isActive;
    }

    @EventTarget(value=4)
    public void onRender(GlRenderEvent glRenderEvent) {
        float fadeFactor;
        float bgAlpha;
        long elapsed;
        if (this.finished) {
            return;
        }
        if (this.startTime < 0L) {
            this.startTime = System.currentTimeMillis();
        }
        long dup = elapsed = System.currentTimeMillis() - this.startTime;
        float screenWidth = mc.getWindow().getGuiScaledWidth();
        float screenHeight = mc.getWindow().getGuiScaledHeight();
        float centerX = screenWidth / 2.0f;
        float centerY = screenHeight / 2.0f;
        long zAppearStart = 1100L;
        long fadeOutStart = zAppearStart + 1200L + 800L + 1200L;
        if (elapsed <= 800L) {
            float fadeIn = IntroAnimation.easeOutCubic(IntroAnimation.clamp01((float)elapsed / 800.0f));
            bgAlpha = 0.6f * fadeIn;
        } else if (elapsed <= fadeOutStart) {
            bgAlpha = 0.6f;
        } else if (elapsed <= fadeOutStart + 700L) {
            float fadeOut = 1.0f - IntroAnimation.easeInCubic(IntroAnimation.clamp01((float)(elapsed - fadeOutStart) / 700.0f));
            bgAlpha = 0.6f * fadeOut;
        } else {
            this.finish();
            return;
        }
        Paint paint = GlHelper.toPaint(new Color(0, 0, 0, (int)(bgAlpha * 255.0f)));
        GlHelper.drawRect(0.0f, 0.0f, screenWidth, screenHeight, paint);

        float textAlpha = 0.0f;
        float textScale = 1.0f;
        if (elapsed >= zAppearStart) {
            long sinceZ = elapsed - zAppearStart;
            if (sinceZ <= 1200L) {
                float tp = IntroAnimation.easeOutCubic(IntroAnimation.clamp01((float)sinceZ / 1200.0f));
                textScale = IntroAnimation.lerp(1.5f, 1.0f, tp);
                textAlpha = tp;
            } else {
                textScale = 1.0f;
                textAlpha = 1.0f;
            }
        }

        fadeFactor = 1.0f;
        if (elapsed > fadeOutStart) {
            fadeFactor = 1.0f - IntroAnimation.clamp01((float)(elapsed - fadeOutStart) / 700.0f);
        }

        String name = NightClient.CLIENT_NAME_UPPER;
        FontRenderer glowFont = FontPresets.axiformaBold(68.0f * textScale);
        FontRenderer mainFont = FontPresets.axiformaBold(64.0f * textScale);
        float totalW = GlHelper.getStringWidth(name, mainFont);
        float renderX = centerX - totalW / 2.0f;
        float renderY = centerY - mainFont.getMetrics().capHeight() / 2.0f;

        // Outer glow layer
        if (textAlpha > 0.01f) {
            float glowAlpha = Math.min(textAlpha * 0.35f, 0.35f);
            int glowColor = new Color(0.5f, 0.7f, 1.0f, IntroAnimation.clamp01(glowAlpha * fadeFactor)).getRGB();
            GlHelper.drawText(name, renderX - 3.0f, renderY, glowFont, glowColor);
            GlHelper.drawText(name, renderX + 3.0f, renderY, glowFont, glowColor);
            GlHelper.drawText(name, renderX, renderY - 2.0f, glowFont, glowColor);
        }

        // Main text
        int textColor = new Color(1.0f, 1.0f, 1.0f, IntroAnimation.clamp01(textAlpha * fadeFactor)).getRGB();
        GlHelper.drawText(name, renderX, renderY, mainFont, textColor);
    }

    private void finish() {
        if (!this.finished) {
            this.finished = true;
            try {
                NightClient.instance.getEventBus().unregister(this);
            } catch (Throwable throwable) {
                // empty catch block
            }
            isActive = false;
        }
    }

    private static float clamp01(float value) {
        return value < 0.0f ? 0.0f : (value > 1.0f ? 1.0f : value);
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }

    private static float easeOutCubic(float t) {
        float clamped = IntroAnimation.clamp01(t);
        clamped = (float)(1.0 - Math.pow(1.0f - clamped, 3.0));
        return clamped;
    }

    private static float easeInCubic(float t) {
        float clamped = IntroAnimation.clamp01(t);
        clamped = clamped * clamped * clamped;
        return clamped;
    }
}
