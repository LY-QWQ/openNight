package client.nilore.hud;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import net.minecraft.client.renderer.texture.DynamicTexture;
import client.nilore.event.impl.GlRenderEvent;
import client.nilore.event.impl.Render2DEvent;
import client.nilore.modules.impl.misc.MusicPlayer;
import client.nilore.modules.impl.misc.music.AudioPlayer;
import client.nilore.modules.impl.misc.music.NeteaseApi;
import client.nilore.modules.impl.misc.music.SongInfo;
import client.nilore.render.DrawContext;
import client.nilore.render.FontPresets;
import client.nilore.render.FontRenderer;
import client.nilore.render.GlHelper;
import client.nilore.render.Paint;
import client.nilore.render.Rectangle;
import client.nilore.render.RoundedRectangle;
import client.nilore.render.RoundedRectShader;
import client.nilore.render.Texture;
import client.nilore.utils.animation.SmoothAnimationTimer;
import client.nilore.utils.math.Easings;
import client.nilore.utils.render.ColorUtil;

public class MusicPlayerHud extends HudElement {
    private final SmoothAnimationTimer showAnim = new SmoothAnimationTimer();
    private float titleScrollOffset = 0;
    private long titleScrollTimestamp = 0;

    private static final float ART_SIZE = 32;
    private static final float GAP = 8;
    private static final float PAD = 8;
    private static final float BAR_H = ART_SIZE + PAD * 2;
    private static final float TEXT_W = 160;
    private static final float BAR_W = PAD + ART_SIZE + GAP + TEXT_W + PAD;
    private static final float RADIUS = 10;
    private static final float BAR_HEIGHT = 2f;

    private final FontRenderer titleFont = FontPresets.pingfang(17);
    private final FontRenderer artistFont = FontPresets.pingfang(14);
    private final FontRenderer timeFont = FontPresets.pingfang(13);

    // album art cache
    private volatile Texture albumTexture;
    private long albumSongId = -1;
    private volatile boolean albumLoading = false;
    private volatile byte[] albumBytes;
    private int albumRetryCount = 0;

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

        // Stop if not in a world (title screen, etc.)
        if (mc.level == null) {
            player.stop();
            showAnim.animate(0.0, 0.3, Easings.EASE_OUT_POW3);
            showAnim.tick();
            this.setWidth(0);
            this.setHeight(0);
            return;
        }
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

        // load album art
        loadAlbumArt(song);
        if (albumBytes != null) {
            try {
                NativeImage img = NativeImage.read(new ByteArrayInputStream(albumBytes));
                DynamicTexture dyn = new DynamicTexture(img);
                albumTexture = new Texture(dyn.getId(), img.getWidth(), img.getHeight());
            } catch (Exception e) {
                System.err.println("[MusicPlayerHud] Failed to create album texture: " + e.getMessage());
            }
            albumBytes = null;
        }

        DrawContext ctx = glRenderEvent.drawContext();
        if (ctx == null) return;

        float progress = player.getProgress();

        // background
        ctx.drawRoundedRect(RoundedRectangle.ofXYWHR(x, y, BAR_W, BAR_H, RADIUS),
                new Paint().setColor(ColorUtil.fromARGB(0, 0, 0, (int)(100 * a))));

        // album art (rounded, equal padding)
        float artX = x + PAD;
        float artY = y + PAD;
        float artRadius = 6;
        if (albumTexture != null) {
            int artColor = ColorUtil.fromARGB(255, 255, 255, (int)(255 * a));
            org.joml.Matrix4f pose = ctx.getPoseStack().last().pose();
            DrawContext.getRoundedRectShader().drawTextured(pose,
                    artX, artY, artX + ART_SIZE, artY + ART_SIZE,
                    artRadius, artRadius, artRadius, artRadius,
                    artColor, albumTexture.getGlId(), 0, 0, 1, 1);
        } else {
            ctx.drawRoundedRect(RoundedRectangle.ofXYWHR(artX, artY, ART_SIZE, ART_SIZE, artRadius),
                    new Paint().setColor(ColorUtil.fromARGB(255, 255, 255, (int)(15 * a))));
        }

        // text area (to the right of album art)
        float textX = artX + ART_SIZE + GAP;
        float textMaxW = TEXT_W;
        String title = song.name;
        float titleW = GlHelper.getStringWidth(title, titleFont);

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

        GlHelper.drawText(song.artist, textX, y + PAD + 12, artistFont,
                ColorUtil.fromARGB(255, 255, 255, (int)(120 * a)));

        float barY = y + BAR_H - PAD - BAR_HEIGHT - 3;
        float barW = textMaxW;

        ctx.drawRoundedRect(RoundedRectangle.ofXYWHR(textX, barY, barW, BAR_HEIGHT, BAR_HEIGHT / 2),
                new Paint().setColor(ColorUtil.fromARGB(255, 255, 255, (int)(30 * a))));

        if (progress > 0.001f) {
            ctx.drawRoundedRect(RoundedRectangle.ofXYWHR(textX, barY, barW * progress, BAR_HEIGHT, BAR_HEIGHT / 2),
                    new Paint().setColor(ColorUtil.fromARGB(255, 255, 255, (int)(180 * a))));
        }

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

    private void loadAlbumArt(SongInfo song) {
        if (albumLoading) return;
        if (song.id == albumSongId && albumTexture != null) return;
        if (song.id == albumSongId && albumRetryCount >= 2) return;
        if (song.id != albumSongId) {
            albumRetryCount = 0;
        }
        albumSongId = song.id;
        albumTexture = null;
        albumBytes = null;
        albumLoading = true;
        albumRetryCount++;

        NeteaseApi.getAlbumPicUrl(song.albumPicUrl).thenAccept(picUrl -> {
            if (picUrl == null || picUrl.isEmpty()) { albumLoading = false; return; }
            try {
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(picUrl))
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0")
                        .GET()
                        .build();
                byte[] bytes = client.send(req, HttpResponse.BodyHandlers.ofByteArray()).body();
                if (bytes != null && bytes.length > 100) {
                    albumBytes = bytes;
                } else {
                    System.err.println("[MusicPlayerHud] Album art too small, retrying...");
                    albumSongId = -1;
                }
            } catch (Exception e) {
                System.err.println("[MusicPlayerHud] Failed to download album art: " + e.getMessage());
                albumSongId = -1;
            } finally {
                albumLoading = false;
            }
        }).exceptionally(e -> {
            albumLoading = false;
            albumSongId = -1;
            return null;
        });
    }

    private String formatMs(long ms) {
        long sec = ms / 1000;
        return String.format("%d:%02d", sec / 60, sec % 60);
    }

    @Override
    public void onSettings() {
    }
}
