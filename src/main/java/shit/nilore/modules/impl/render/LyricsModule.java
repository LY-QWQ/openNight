package shit.nilore.modules.impl.render;

import java.util.Collections;
import java.util.List;
import shit.nilore.event.EventTarget;
import shit.nilore.event.impl.GlRenderEvent;
import shit.nilore.modules.Category;
import shit.nilore.modules.Module;
import shit.nilore.modules.impl.misc.MusicPlayer;
import shit.nilore.modules.impl.misc.music.AudioPlayer;
import shit.nilore.modules.impl.misc.music.LyricLine;
import shit.nilore.modules.impl.misc.music.NeteaseApi;
import shit.nilore.modules.impl.misc.music.SongInfo;
import shit.nilore.render.DrawContext;
import shit.nilore.render.FontPresets;
import shit.nilore.render.FontRenderer;
import shit.nilore.settings.impl.NumberSetting;
import shit.nilore.render.GlHelper;
import shit.nilore.render.Rectangle;
import shit.nilore.utils.animation.SmoothAnimationTimer;
import shit.nilore.utils.math.Easings;
import shit.nilore.utils.render.ColorUtil;

public class LyricsModule extends Module {
    private volatile List<LyricLine> lyrics = Collections.emptyList();
    private long lyricsSongId = -1;
    private int currentLyricIndex = -1;
    private final SmoothAnimationTimer scrollAnim = new SmoothAnimationTimer();
    private final SmoothAnimationTimer alphaAnim = new SmoothAnimationTimer();
    private float alpha = 0;
    private volatile boolean needsWarmup = false;

    private final NumberSetting yOffset = new NumberSetting("Y Offset", 0, -200, 200, 1);

    private static final float PANEL_H = 130;
    private static final float LINE_SPACING = 24;

    private final FontRenderer currentFont = FontPresets.pingfang(19);
    private final FontRenderer nearFont = FontPresets.pingfang(16);
    private final FontRenderer farFont = FontPresets.pingfang(14);

    public LyricsModule() {
        super("Lyrics", Category.RENDER);
    }

    @EventTarget
    public void onGlRender(GlRenderEvent event) {
        AudioPlayer player = MusicPlayer.AUDIO_PLAYER;
        SongInfo song = player.getCurrentSong();
        boolean playing = song != null && player.getState() != AudioPlayer.State.STOPPED;

        List<LyricLine> snapshot = lyrics;

        // pre-warm font glyphs on render thread (first frame after lyrics load)
        if (needsWarmup) {
            needsWarmup = false;
            warmupFonts(snapshot);
        }

        alphaAnim.animate(playing && !snapshot.isEmpty() ? 1.0 : 0.0, 0.4, Easings.EASE_OUT_QUAD);
        alphaAnim.tick();
        alpha = alphaAnim.getValueF();
        if (alpha < 0.01f) return;

        if (song != null) loadLyricsIfNeeded(song.id);

        DrawContext ctx = event.drawContext();
        if (ctx == null) return;

        float screenW = mc.getWindow().getGuiScaledWidth();
        float screenH = mc.getWindow().getGuiScaledHeight();
        float centerX = screenW / 2f;
        float panelCenterY = screenH / 2f + yOffset.getValue().floatValue();
        float panelTop = panelCenterY - PANEL_H / 2f;

        ctx.save();
        ctx.clipRect(Rectangle.ofXYWH(0, panelTop, screenW, PANEL_H), true);

        long currentMs = player.getCurrentPositionMs();
        int newIdx = findLyricIndex(currentMs, snapshot);
        if (newIdx != currentLyricIndex) {
            currentLyricIndex = newIdx;
            if (currentLyricIndex >= 0) {
                scrollAnim.animate(currentLyricIndex * LINE_SPACING, 0.45, Easings.EASE_OUT_QUAD);
            }
        }
        scrollAnim.tick();
        float scrollY = scrollAnim.getValueF();

        for (int i = 0; i < snapshot.size(); i++) {
            String text = snapshot.get(i).text();
            float lineY = panelCenterY + (i * LINE_SPACING) - scrollY;

            if (lineY < panelTop - 30 || lineY > panelTop + PANEL_H + 10) continue;

            int dist = Math.abs(i - currentLyricIndex);
            FontRenderer font;
            int textAlpha;

            if (dist == 0) {
                font = currentFont;
                textAlpha = (int)(255 * alpha);
            } else if (dist == 1) {
                font = nearFont;
                textAlpha = (int)(130 * alpha);
            } else if (dist == 2) {
                font = farFont;
                textAlpha = (int)(70 * alpha);
            } else {
                font = farFont;
                textAlpha = (int)(35 * alpha);
            }

            float tw = GlHelper.getStringWidth(text, font);
            float textX = centerX - tw / 2f;
            float textBaseline = lineY - GlHelper.getFontAscent(font) / 2f;

            GlHelper.drawText(text, textX, textBaseline, font,
                    ColorUtil.fromARGB(255, 255, 255, textAlpha));
        }

        ctx.restore();
    }

    /** Force all glyph pages to be built for every character in the lyrics. */
    private void warmupFonts(List<LyricLine> snapshot) {
        for (LyricLine line : snapshot) {
            String text = line.text();
            currentFont.getWidth(text);
            nearFont.getWidth(text);
            farFont.getWidth(text);
        }
    }

    public void setLyrics(long songId, List<LyricLine> newLyrics) {
        lyricsSongId = songId;
        lyrics = List.copyOf(newLyrics);
        currentLyricIndex = -1;
        needsWarmup = true;
        scrollAnim.animate(0, 0);
    }

    private void loadLyricsIfNeeded(long songId) {
        if (songId == lyricsSongId) return;
        lyricsSongId = songId;
        lyrics = Collections.emptyList();
        currentLyricIndex = -1;
        scrollAnim.animate(0, 0);

        NeteaseApi.getLyrics(songId).thenAccept(result -> {
            lyrics = List.copyOf(result);
            currentLyricIndex = -1;
            needsWarmup = true;
        }).exceptionally(e -> {
            System.err.println("[Lyrics] Failed to load lyrics: " + e.getMessage());
            return null;
        });
    }

    private int findLyricIndex(long currentMs, List<LyricLine> list) {
        int idx = -1;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).timeMs() <= currentMs) {
                idx = i;
            } else {
                break;
            }
        }
        return idx;
    }
}
