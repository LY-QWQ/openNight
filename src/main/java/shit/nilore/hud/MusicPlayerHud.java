package shit.nilore.hud;

import shit.nilore.event.impl.GlRenderEvent;
import shit.nilore.event.impl.Render2DEvent;
import shit.nilore.modules.impl.misc.MusicPlayer;
import shit.nilore.modules.impl.misc.music.AudioPlayer;
import shit.nilore.modules.impl.misc.music.SongInfo;
import shit.nilore.render.DrawContext;
import shit.nilore.render.FontPresets;
import shit.nilore.render.FontRenderer;
import shit.nilore.render.GlHelper;
import shit.nilore.render.Paint;
import shit.nilore.render.Rectangle;
import shit.nilore.render.RoundedRectangle;
import shit.nilore.utils.animation.SmoothAnimationTimer;
import shit.nilore.utils.math.Easings;
import shit.nilore.utils.render.ColorUtil;

public class MusicPlayerHud extends HudElement {
    private final SmoothAnimationTimer showAnim = new SmoothAnimationTimer();
    private float titleScrollOffset = 0;
    private long titleScrollTimestamp = 0;

    private static final float BAR_W = 220;
    private static final float BAR_H = 44;
    private static final float RADIUS = 10;
    private static final float PAD = 10;
    private static final float BAR_HEIGHT = 2f;

    private final FontRenderer titleFont = FontPresets.axiformaBold(13);
    private final FontRenderer artistFont = FontPresets.axiformaRegular(10);
    private final FontRenderer timeFont = FontPresets.axiformaRegular(9);

    public MusicPlayerHud() {
        super("MusicPlayerHud");
        this.setWidth(BAR_W);
        this.setHeight(BAR_H);
    }

    @Override
    public void onRender2D(Render2DEvent render2DEvent, float x, float y) {
    }

    @Override
    public void onGlRender(GlRenderEvent glRenderEvent, float x, float y) {
        AudioPlayer player = MusicPlayer.AUDIO_PLAYER;
        boolean hasSong = player.getCurrentSong() != null && player.getState() != AudioPlayer.State.STOPPED;

        showAnim.animate(hasSong ? 1.0 : 0.0, 0.3, Easings.EASE_OUT_POW3);
        showAnim.tick();
        float a = showAnim.getValueF();
        if (a < 0.01f) {
            this.setWidth(0);
            this.setHeight(0);
            return;
        }

        SongInfo song = player.getCurrentSong();
        if (song == null) return;

        DrawContext ctx = glRenderEvent.drawContext();
        if (ctx == null) return;

        float progress = player.getProgress();

        // background — black, semi-transparent
        ctx.drawRoundedRect(RoundedRectangle.ofXYWHR(x, y, BAR_W, BAR_H, RADIUS),
                new Paint().setColor(ColorUtil.fromARGB(0, 0, 0, (int)(100 * a))));

        // song title — scroll if too long
        float textX = x + PAD;
        float textMaxW = BAR_W - PAD * 2;
        String title = song.name;
        float titleW = GlHelper.getStringWidth(title, titleFont);

        // clip rect — extra height above baseline to avoid clipping ascenders
        ctx.save();
        ctx.clipRect(Rectangle.ofXYWH(textX, y + 2, textMaxW, BAR_H - 4), true);
        GlHelper.drawText(title, textX - titleScrollOffset, y + PAD, titleFont,
                ColorUtil.fromARGB(255, 255, 255, (int)(255 * a)));
        ctx.restore();

        if (titleW > textMaxW) {
            if (System.currentTimeMillis() - titleScrollTimestamp > 2000) {
                titleScrollOffset += 0.3f;
                if (titleScrollOffset > titleW + 16) {
                    titleScrollOffset = -textMaxW;
                    titleScrollTimestamp = System.currentTimeMillis();
                }
            }
        } else {
            titleScrollOffset = 0;
            titleScrollTimestamp = System.currentTimeMillis();
        }

        // artist — gray, moved up
        GlHelper.drawText(song.artist, textX, y + PAD + 12, artistFont,
                ColorUtil.fromARGB(255, 255, 255, (int)(120 * a)));

        // progress bar — bottom, equal padding
        float barY = y + BAR_H - PAD - BAR_HEIGHT;
        float barW = BAR_W - PAD * 2;

        // track — dark gray
        ctx.drawRoundedRect(RoundedRectangle.ofXYWHR(textX, barY, barW, BAR_HEIGHT, BAR_HEIGHT / 2),
                new Paint().setColor(ColorUtil.fromARGB(255, 255, 255, (int)(30 * a))));

        // fill — white, direct progress (no animation delay)
        if (progress > 0.001f) {
            ctx.drawRoundedRect(RoundedRectangle.ofXYWHR(textX, barY, barW * progress, BAR_HEIGHT, BAR_HEIGHT / 2),
                    new Paint().setColor(ColorUtil.fromARGB(255, 255, 255, (int)(180 * a))));
        }

        // time labels — left: elapsed, right: total, aligned with progress bar ends
        String elapsed = formatMs(player.getCurrentPositionMs());
        String total = song.formatDuration();
        int timeAlpha = (int)(100 * a);
        GlHelper.drawText(elapsed, textX, barY + 5, timeFont,
                ColorUtil.fromARGB(255, 255, 255, timeAlpha));
        float totalW = GlHelper.getStringWidth(total, timeFont);
        GlHelper.drawText(total, textX + barW - totalW, barY + 5, timeFont,
                ColorUtil.fromARGB(255, 255, 255, timeAlpha));

        this.setWidth(BAR_W);
        this.setHeight(BAR_H);
    }

    private String formatMs(long ms) {
        long sec = ms / 1000;
        return String.format("%d:%02d", sec / 60, sec % 60);
    }

    @Override
    public void onSettings() {
    }
}
