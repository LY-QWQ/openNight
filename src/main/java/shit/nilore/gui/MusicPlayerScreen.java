package shit.nilore.gui;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import shit.nilore.NiloreClient;
import shit.nilore.modules.impl.misc.MusicPlayer;
import shit.nilore.modules.impl.misc.music.AudioPlayer;
import shit.nilore.modules.impl.misc.music.LyricLine;
import shit.nilore.modules.impl.misc.music.NeteaseApi;
import shit.nilore.modules.impl.misc.music.SongInfo;
import shit.nilore.modules.impl.render.LyricsModule;
import shit.nilore.render.DrawContext;
import shit.nilore.render.FontPresets;
import shit.nilore.render.FontRenderer;
import shit.nilore.render.GlHelper;
import shit.nilore.render.Paint;
import shit.nilore.render.Rectangle;
import shit.nilore.render.Renderer;
import shit.nilore.render.RoundedRectangle;
import shit.nilore.render.Texture;
import shit.nilore.utils.animation.SmoothAnimationTimer;
import shit.nilore.utils.math.Easings;
import shit.nilore.utils.render.ColorUtil;

public class MusicPlayerScreen extends Screen {
    // Fonts — FangSong style (pingfang as available Chinese font)
    private static final FontRenderer TITLE_FONT = FontPresets.pingfang(22.0f);
    private static final FontRenderer HEADING_FONT = FontPresets.pingfang(16.0f);
    private static final FontRenderer BODY_FONT = FontPresets.pingfang(16.0f);
    private static final FontRenderer INPUT_FONT = FontPresets.pingfang(24.0f);
    private static final FontRenderer SMALL_FONT = FontPresets.pingfang(14.0f);
    private static final FontRenderer ICON_FONT = FontPresets.materialIcons(20.0f);
    private static final FontRenderer SIDEBAR_FONT = FontPresets.productSans(18.0f);

    // Layout
    private static final float SIDEBAR_W = 160.0f;
    private static final float BOTTOM_H = 72.0f;
    private static final float RADIUS = 12.0f;
    private static final float PAD = 16.0f;
    private static final float GAP = 8.0f;

    // State
    private Page page = Page.SEARCH;
    private final List<ClickArea> clickAreas = new ArrayList<>();
    private final Map<Long, List<LyricLine>> lyricsCache = new ConcurrentHashMap<>();
    private final Map<Long, Boolean> lyricsRequested = new ConcurrentHashMap<>();
    private final AtomicLong searchSeq = new AtomicLong();

    private String searchText = "";
    private boolean searchFocused;
    private boolean searchDirty;
    private boolean searchSelectAll;
    private long lastSearchEditMs;
    private volatile List<SongInfo> searchResults = List.of();
    private volatile boolean searching;

    private final List<SongInfo> playQueue = new ArrayList<>();
    private int queueIndex = -1;

    private float searchScroll;
    private float maxSearchScroll;
    private float queueScroll;
    private float maxQueueScroll;
    private float lyricScroll;

    private DragTarget dragTarget = DragTarget.NONE;
    private Rectangle lastProgressRect;
    private Rectangle lastVolumeRect;
    private float pendingVolume = -1.0f;

    private final SmoothAnimationTimer openAnim = new SmoothAnimationTimer();

    // Album art state
    private volatile Texture albumTexture;
    private long albumSongId = -1;
    private volatile boolean albumLoading = false;
    private volatile byte[] albumBytes;
    private int albumRetryCount = 0;

    public MusicPlayerScreen() {
        super(Component.literal("Music Player"));
    }

    @Override
    public void tick() {
        if (this.searchDirty && this.searchFocused
                && System.currentTimeMillis() - this.lastSearchEditMs >= 350) {
            this.startSearch();
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.clickAreas.clear();
        this.openAnim.animate(1.0, 0.3, Easings.EASE_OUT_QUAD);
        this.openAnim.tick();
        float openProgress = this.openAnim.getValueF();

        Renderer.render(guiGraphics, ctx -> {
            float outerW = Math.max(360.0f, Math.min(this.width - 32.0f, 960.0f));
            float outerH = Math.max(260.0f, Math.min(this.height - 32.0f, 540.0f));
            float ox = (this.width - outerW) * 0.5f;
            float oy = (this.height - outerH) * 0.5f;

            // Overlay
            ctx.drawRectXYWH(0, 0, this.width, this.height,
                    new Paint().setColor(ColorUtil.fromARGB(0, 0, 0, (int) (80 * openProgress))));

            // Main panel — dual layer
            drawGlowPanel(ctx, ox, oy, outerW, outerH, RADIUS, openProgress);

            float contentX = ox + SIDEBAR_W;
            float contentY = oy;
            float contentW = outerW - SIDEBAR_W;
            float contentH = outerH - BOTTOM_H;

            // Sidebar
            renderSidebar(ctx, ox, oy, SIDEBAR_W, contentH, mouseX, mouseY);

            // Content
            switch (this.page) {
                case PLAYER -> renderPlayer(ctx, contentX, contentY, contentW, contentH, mouseX, mouseY);
                case SEARCH -> renderSearch(ctx, contentX, contentY, contentW, contentH, mouseX, mouseY);
                case QUEUE -> renderQueue(ctx, contentX, contentY, contentW, contentH, mouseX, mouseY);
                case ABOUT -> renderAbout(ctx, contentX, contentY, contentW, contentH);
            }

            // Bottom bar
            renderBottomBar(ctx, ox, contentY + contentH, outerW, BOTTOM_H, mouseX, mouseY);

            // Loading overlay
            if (searching) {
                String loading = "Searching...";
                float lw = GlHelper.getStringWidth(loading, BODY_FONT);
                GlHelper.drawText(loading, ox + (outerW - lw) / 2f, oy + outerH / 2f, BODY_FONT, 0xFFAAAAAA);
            }
        });
    }

    // --- Sidebar ---

    private void renderSidebar(DrawContext ctx, float x, float y, float w, float h, int mx, int my) {
        GlHelper.drawTextWithShadow("Music", x + PAD, y + 20, HEADING_FONT,
                new Paint().setColor(0xFFFFFFFF));
        GlHelper.drawText("Player", x + PAD, y + 40, SMALL_FONT, 0xFFAAAAAA);

        float navY = y + 64;
        float navH = 30;
        float navGap = 34;
        navItem(ctx, x + PAD, navY, w - PAD * 2, navH, Page.PLAYER, "Player", mx, my);
        navItem(ctx, x + PAD, navY + navGap, w - PAD * 2, navH, Page.SEARCH, "Search", mx, my);
        navItem(ctx, x + PAD, navY + navGap * 2, w - PAD * 2, navH, Page.QUEUE, "Queue", mx, my);
        navItem(ctx, x + PAD, navY + navGap * 3, w - PAD * 2, navH, Page.ABOUT, "About", mx, my);
    }

    private void navItem(DrawContext ctx, float x, float y, float w, float h, Page target, String label, int mx, int my) {
        boolean active = this.page == target;
        boolean hover = contains(mx, my, x, y, w, h);

        if (active) {
            rounded(ctx, x, y, w, h, 6, new int[]{255, 255, 255, 25});
        } else if (hover) {
            rounded(ctx, x, y, w, h, 6, new int[]{255, 255, 255, 12});
        }

        int textColor = active ? 0xFFFFFFFF : hover ? 0xFFCCCCCC : 0xFFAAAAAA;
        GlHelper.drawText(label, x + PAD, y + (h - 14) / 2 + 7, SIDEBAR_FONT, textColor);
        if (active) {
            GlHelper.drawText(label, x + PAD, y + (h - 14) / 2 + 7, SIDEBAR_FONT,
                    ColorUtil.fromARGB(255, 255, 255, 150));
        }

        this.clickAreas.add(new ClickArea(x, y, w, h, () -> this.page = target));
    }

    // --- Search Page ---

    private void renderSearch(DrawContext ctx, float x, float y, float w, float h, int mx, int my) {
        float pad = PAD;
        GlHelper.drawTextWithShadow("Search", x + pad, y + pad + 14, TITLE_FONT,
                new Paint().setColor(0xFFFFFFFF));

        float inputX = x + pad;
        float inputY = y + pad + 48;
        float buttonWidth = 70;
        float inputHeight = 40;
        float inputWidth = w - pad * 2;

        // Input field — dual layer background
        rounded(ctx, inputX, inputY, inputWidth, inputHeight, 8, new int[]{24, 24, 24, 150});
        rounded(ctx, inputX, inputY, inputWidth, inputHeight, 8, new int[]{255, 255, 255, 20});

        float textY = inputY + (inputHeight - 24) / 2 + 10;
        String displayText = this.searchText.isEmpty() && !this.searchFocused ? "  Search songs..." : this.searchText;
        if (this.searchFocused) {
            float cursorX = Math.min(inputX + inputWidth - buttonWidth - 10, inputX + 14 + measure(displayText, INPUT_FONT) + 2);
            float cursorH = 18;
            float cursorTop = inputY + (inputHeight - cursorH) / 2;
            float cursorBottom = cursorTop + cursorH;
            if (this.searchSelectAll && !this.searchText.isEmpty()) {
                float selW = Math.min(inputWidth - buttonWidth - 30, measure(this.searchText, INPUT_FONT) + 4);
                rounded(ctx, inputX + 12, cursorTop, selW, cursorBottom - cursorTop, 4, new int[]{255, 255, 255, 40});
            } else {
                float blink = (float) Math.abs(Math.sin(System.currentTimeMillis() / 200.0));
                ctx.drawLine(cursorX, cursorTop, cursorX, cursorBottom,
                        new Paint().setColor(ColorUtil.fromARGB(255, 255, 255, (int) (180 * blink))).setStrokeWidth(1));
            }
        }
        int inputTextAlpha = this.searchText.isEmpty() && !this.searchFocused ? 120 : 180;
        GlHelper.drawText(ellipsize(displayText, 40), inputX-8, textY, INPUT_FONT,
                ColorUtil.fromARGB(255, 255, 255, inputTextAlpha));
        this.clickAreas.add(new ClickArea(inputX, inputY, inputWidth - buttonWidth, inputHeight, () -> {
            this.searchFocused = true;
            this.searchSelectAll = false;
        }));

        // Search button — vertically centered in input
        float btnPad = 4;
        float btnX = inputX + inputWidth - buttonWidth + btnPad;
        float btnH = 28;
        float btnY = inputY + (inputHeight - btnH) / 2;
        {
            boolean hover = contains(mx, my, btnX, btnY, buttonWidth - btnPad * 2, btnH);
            rounded(ctx, btnX, btnY, buttonWidth - btnPad * 2, btnH, 6,
                    hover ? new int[]{255, 255, 255, 35} : new int[]{255, 255, 255, 18});
            float btnTextY = btnY + (btnH - 12) / 2 + 4;
            float btnTextW = GlHelper.getStringWidth(this.searching ? "..." : "Search", SMALL_FONT);
            GlHelper.drawText(this.searching ? "..." : "Search", btnX + (buttonWidth - btnPad * 2 - btnTextW) / 2, btnTextY, SMALL_FONT,
                    hover ? 0xFFFFFFFF : 0xFFCCCCCC);
            this.clickAreas.add(new ClickArea(btnX, btnY, buttonWidth - btnPad * 2, btnH, this::startSearch));
        }

        // Results
        float listX = inputX;
        float listY = inputY + inputHeight + 12;
        float rowH = 46;
        float listW = inputWidth;
        float listH = h - (listY - y) - pad;
        this.maxSearchScroll = Math.max(0, this.searchResults.size() * (rowH + 2) - 2 - listH);
        this.searchScroll = clamp(this.searchScroll, 0, this.maxSearchScroll);

        ctx.save();
        ctx.clipRect(Rectangle.ofXYWH(listX, listY, listW, listH), true);

        if (this.searchResults.isEmpty() && !searching) {
            GlHelper.drawText("Type a keyword and press Enter.", listX + 12, listY + 14, BODY_FONT, 0xFF888888);
        } else {
            float yy = listY - this.searchScroll;
            AudioPlayer player = MusicPlayer.AUDIO_PLAYER;
            SongInfo playing = player.getCurrentSong();
            for (int i = 0; i < this.searchResults.size(); i++) {
                SongInfo song = this.searchResults.get(i);
                if (yy + rowH < listY) { yy += rowH + 2; continue; }
                if (yy > listY + listH) break;

                boolean hover = contains(mx, my, listX, yy, listW, rowH);
                boolean isPlaying = playing != null && playing.id == song.id;

                if (isPlaying) {
                    rounded(ctx, listX, yy, listW, rowH, 6, new int[]{255, 255, 255, 20});
                } else if (hover) {
                    rounded(ctx, listX, yy, listW, rowH, 6, new int[]{255, 255, 255, 10});
                }

                int nameColor = isPlaying ? 0xFFFFFFFF : hover ? 0xFFDDDDDD : 0xFFAAAAAA;
                GlHelper.drawText(ellipsize(song.name, 36), listX + 12, yy + 12, isPlaying ? HEADING_FONT : BODY_FONT, nameColor);
                GlHelper.drawText(ellipsize(song.artist, 40), listX + 12, yy + 30, SMALL_FONT, 0xFF888888);

                if (song.duration > 0) {
                    String dur = song.formatDuration();
                    float durW = GlHelper.getStringWidth(dur, SMALL_FONT);
                    GlHelper.drawText(dur, listX + listW - durW - 12, yy + 16, SMALL_FONT, 0xFF666666);
                }

                int fi = i;
                this.clickAreas.add(new ClickArea(listX, yy, listW, rowH, () -> playSong(this.searchResults.get(fi))));
                yy += rowH + 2;
            }
        }

        ctx.restore();
    }

    // --- Player Page ---

    private void renderPlayer(DrawContext ctx, float x, float y, float w, float h, int mx, int my) {
        AudioPlayer player = MusicPlayer.AUDIO_PLAYER;
        SongInfo song = player.getCurrentSong();

        // Load album art
        if (song != null) {
            loadAlbumArt(song);
            if (albumBytes != null) {
                try {
                    NativeImage img = NativeImage.read(new ByteArrayInputStream(albumBytes));
                    DynamicTexture dyn = new DynamicTexture(img);
                    albumTexture = new Texture(dyn.getId(), img.getWidth(), img.getHeight());
                } catch (Exception e) {
                    System.err.println("[MusicPlayerScreen] Failed to create album texture: " + e.getMessage());
                }
                albumBytes = null;
            }
        }

        GlHelper.drawTextWithShadow("Now Playing", x + PAD, y + PAD, TITLE_FONT,
                new Paint().setColor(0xFFFFFFFF));

        float coverSize = Math.min(180, Math.max(120, w * 0.25f));
        float coverX = x + PAD;
        float coverY = y + 52;

        // Draw album art or placeholder
        if (albumTexture != null) {
            org.joml.Matrix4f pose = ctx.getPoseStack().last().pose();
            DrawContext.getRoundedRectShader().drawTextured(pose,
                    coverX, coverY, coverX + coverSize, coverY + coverSize,
                    10, 10, 10, 10,
                    0xFFFFFFFF, albumTexture.getGlId(), 0, 0, 1, 1);
        } else {
            rounded(ctx, coverX, coverY, coverSize, coverSize, 10, new int[]{24, 24, 24, 150});
            rounded(ctx, coverX, coverY, coverSize, coverSize, 10, new int[]{255, 255, 255, 15});
        }

        if (song != null) {
            GlHelper.drawText(ellipsize(song.name, 24), coverX, coverY + coverSize + GAP, HEADING_FONT, 0xFFFFFFFF);
            GlHelper.drawText(ellipsize(song.artist, 30), coverX, coverY + coverSize + GAP + 22, BODY_FONT, 0xFFAAAAAA);
        } else {
            GlHelper.drawText("No track playing", coverX, coverY + coverSize + GAP, HEADING_FONT, 0xFFFFFFFF);
            GlHelper.drawText("Search a song to start.", coverX, coverY + coverSize + GAP + 22, BODY_FONT, 0xFFAAAAAA);
        }

        float lyricsX = coverX + coverSize + 20;
        float lyricsY = y + 52;
        float lyricsW = w - (lyricsX - x) - PAD;
        float lyricsH = h - 82;

        rounded(ctx, lyricsX, lyricsY, lyricsW, lyricsH, 10, new int[]{24, 24, 24, 150});
        rounded(ctx, lyricsX, lyricsY, lyricsW, lyricsH, 10, new int[]{255, 255, 255, 10});

        renderLyrics(ctx, song, lyricsX + 14, lyricsY + 12, lyricsW - 28, lyricsH - 24);
    }

    private void renderLyrics(DrawContext ctx, SongInfo song, float x, float y, float w, float h) {
        if (song == null) {
            GlHelper.drawText("Lyrics will appear here.", x, y, BODY_FONT, 0xFF888888);
            return;
        }

        List<LyricLine> lines = lyricsFor(song);
        if (lines.isEmpty()) {
            GlHelper.drawText("No lyrics available.", x, y, BODY_FONT, 0xFF888888);
            return;
        }

        AudioPlayer player = MusicPlayer.AUDIO_PLAYER;
        long posMs = player.getCurrentPositionMs();
        int current = findCurrentLyricLine(lines, posMs);

        float lineH = 26;
        float targetScroll = Math.max(0, current * lineH - h * 0.42f);
        this.lyricScroll += (targetScroll - this.lyricScroll) * 0.18f;

        ctx.save();
        ctx.clipRect(Rectangle.ofXYWH(x, y, w, h), true);

        float yy = y - this.lyricScroll;
        for (int i = 0; i < lines.size(); i++) {
            LyricLine line = lines.get(i);
            if (yy > y - lineH && yy < y + h + lineH) {
                boolean active = i == current;
                String display = timestamp(line.timeMs()) + "  " + line.text();
                GlHelper.drawText(ellipsize(display, 60), x, yy + 3,
                        active ? HEADING_FONT : SMALL_FONT, active ? 0xFFFFFFFF : 0xFF888888);
            }
            yy += lineH;
        }

        ctx.restore();
    }

    private int findCurrentLyricLine(List<LyricLine> lines, long posMs) {
        int idx = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).timeMs() <= posMs) {
                idx = i;
            } else {
                break;
            }
        }
        return Math.max(0, idx);
    }

    private List<LyricLine> lyricsFor(SongInfo song) {
        List<LyricLine> cached = this.lyricsCache.get(song.id);
        if (cached != null) return cached;

        if (this.lyricsRequested.putIfAbsent(song.id, Boolean.TRUE) == null) {
            NeteaseApi.getLyrics(song.id).thenAccept(lines -> {
                this.lyricsCache.put(song.id, lines == null ? List.of() : lines);
            });
        }
        return List.of();
    }

    // --- Queue Page ---

    private void renderQueue(DrawContext ctx, float x, float y, float w, float h, int mx, int my) {
        GlHelper.drawTextWithShadow("Queue", x + PAD, y + PAD, TITLE_FONT,
                new Paint().setColor(0xFFFFFFFF));

        if (!playQueue.isEmpty()) {
            String count = playQueue.size() + " tracks";
            float countW = GlHelper.getStringWidth(count, SMALL_FONT);
            GlHelper.drawText(count, x + w - countW - PAD, y + 22, SMALL_FONT, 0xFF888888);
        }

        float listX = x + PAD;
        float listY = y + 48;
        float rowH = 46;
        float listW = w - PAD * 2;
        float listH = h - 68;
        this.maxQueueScroll = Math.max(0, playQueue.size() * (rowH + 2) - 2 - listH);
        this.queueScroll = clamp(this.queueScroll, 0, this.maxQueueScroll);

        ctx.save();
        ctx.clipRect(Rectangle.ofXYWH(listX, listY, listW, listH), true);

        if (playQueue.isEmpty()) {
            GlHelper.drawText("Queue is empty.", listX + 12, listY + 14, BODY_FONT, 0xFF888888);
        } else {
            AudioPlayer player = MusicPlayer.AUDIO_PLAYER;
            SongInfo playing = player.getCurrentSong();
            float yy = listY - this.queueScroll;
            for (int i = 0; i < playQueue.size(); i++) {
                SongInfo song = playQueue.get(i);
                if (yy + rowH < listY) { yy += rowH + 2; continue; }
                if (yy > listY + listH) break;

                boolean hover = contains(mx, my, listX, yy, listW, rowH);
                boolean isPlaying = playing != null && playing.id == song.id;
                boolean isCurrent = i == queueIndex;

                if (isPlaying) {
                    rounded(ctx, listX, yy, listW, rowH, 6, new int[]{255, 255, 255, 20});
                } else if (hover) {
                    rounded(ctx, listX, yy, listW, rowH, 6, new int[]{255, 255, 255, 10});
                }

                String index = String.valueOf(i + 1);
                float idxW = GlHelper.getStringWidth(index, SMALL_FONT);
                GlHelper.drawText(index, listX + 12, yy + 16, SMALL_FONT, isCurrent ? 0xFFFFFFFF : 0xFF666666);

                int nameColor = isPlaying ? 0xFFFFFFFF : hover ? 0xFFDDDDDD : 0xFFAAAAAA;
                GlHelper.drawText(ellipsize(song.name, 32), listX + 12 + idxW + 12, yy + 8, isPlaying ? HEADING_FONT : BODY_FONT, nameColor);
                GlHelper.drawText(ellipsize(song.artist, 36), listX + 12 + idxW + 12, yy + 26, SMALL_FONT, 0xFF888888);

                if (song.duration > 0) {
                    String dur = song.formatDuration();
                    float durW = GlHelper.getStringWidth(dur, SMALL_FONT);
                    GlHelper.drawText(dur, listX + listW - durW - 12, yy + 16, SMALL_FONT, 0xFF666666);
                }

                int fi = i;
                this.clickAreas.add(new ClickArea(listX, yy, listW, rowH, () -> {
                    this.queueIndex = fi;
                    playSong(this.playQueue.get(fi));
                }));
                yy += rowH + 2;
            }
        }

        ctx.restore();
    }

    // --- About Page ---

    private void renderAbout(DrawContext ctx, float x, float y, float w, float h) {
        GlHelper.drawTextWithShadow("About", x + PAD, y + PAD, TITLE_FONT,
                new Paint().setColor(0xFFFFFFFF));

        float cardY = y + 52;
        float halfW = (w - PAD * 2 - GAP) * 0.5f;

        aboutCard(ctx, x + PAD, cardY, halfW, 100, "Music Player",
                "An in-game music player using\nNeteaseCloudMusic API.\nSearch, queue, and play music\ninside Minecraft.");
        aboutCard(ctx, x + PAD + halfW + GAP, cardY, halfW, 100, "Notice",
                "For personal learning and testing.\nThe client does not host or\ndistribute music content.");

        aboutCard(ctx, x + PAD, cardY + 116, w - PAD * 2, 70, "Credits",
                "Music API: gdstudio.xyz\nBased on NeteaseCloudMusic public API.");
    }

    private void aboutCard(DrawContext ctx, float x, float y, float w, float h, String title, String body) {
        rounded(ctx, x, y, w, h, 8, new int[]{24, 24, 24, 150});
        rounded(ctx, x, y, w, h, 8, new int[]{255, 255, 255, 12});
        GlHelper.drawText(title, x + 14, y + 12, HEADING_FONT, 0xFFFFFFFF);

        String[] lines = body.split("\\n");
        float yy = y + 34;
        for (String line : lines) {
            GlHelper.drawText(ellipsize(line, 48), x + 14, yy, SMALL_FONT, 0xFFAAAAAA);
            yy += 18;
        }
    }

    // --- Bottom Bar ---

    private void renderBottomBar(DrawContext ctx, float x, float y, float w, float h, int mx, int my) {
        AudioPlayer player = MusicPlayer.AUDIO_PLAYER;
        SongInfo currentSong = player.getCurrentSong();

        // Bottom bar background — bottom rounded corners only
        ctx.drawRoundedRect(RoundedRectangle.ofXYWHRadii(x, y, w, h, new float[]{0, 0, RADIUS, RADIUS}),
                new Paint().setColor(ColorUtil.fromARGB(20, 20, 20, 200)));

        // Song info — left side
        if (currentSong != null) {
            GlHelper.drawText(ellipsize(currentSong.name, 20), x + PAD, y + 10, BODY_FONT, 0xFFFFFFFF);
            GlHelper.drawText(ellipsize(currentSong.artist, 24), x + PAD, y + 30, SMALL_FONT, 0xFFAAAAAA);
        } else {
            GlHelper.drawText("No track", x + PAD, y + 10, BODY_FONT, 0xFFFFFFFF);
            GlHelper.drawText("Music Player", x + PAD, y + 30, SMALL_FONT, 0xFFAAAAAA);
        }

        // Controls — centered, above progress bar
        float controlCenterX = x + w * 0.5f;
        float controlY = y + 10;
        drawControlBtn(ctx, controlCenterX - 50, controlY, 36, 26, "|<", mx, my, this::prevSong);
        drawControlBtn(ctx, controlCenterX - 8, controlY - 2, 40, 30, player.getState() == AudioPlayer.State.PLAYING ? "||" : ">", mx, my, player::togglePause);
        drawControlBtn(ctx, controlCenterX + 38, controlY, 36, 26, ">|", mx, my, this::nextSong);

        // Progress bar — below controls, centered; time labels aligned with track center
        float progressBarX = controlCenterX - 120;
        float progressBarWidth = 240;
        float progressBarY = y + 48;
        float trackCenterOffset = 1.5f; // half of 3px track height

        float progress = player.getProgress();
        long currentMs = player.getCurrentPositionMs();
        long totalMs = currentSong != null ? currentSong.duration : 0;

        String currentTimeStr = timestamp(currentMs);
        String totalTimeStr = timestamp(totalMs);
        float timeY = progressBarY - trackCenterOffset + 2;
        float currentTimeWidth = GlHelper.getStringWidth(currentTimeStr, SMALL_FONT);
        GlHelper.drawText(currentTimeStr, progressBarX - currentTimeWidth - 8, timeY, SMALL_FONT, 0xFF666666);
        drawSlider(ctx, progressBarX, progressBarY, progressBarWidth, progress);
        GlHelper.drawText(totalTimeStr, progressBarX + progressBarWidth + 8, timeY, SMALL_FONT, 0xFF666666);
        this.lastProgressRect = Rectangle.ofXYWH(progressBarX, progressBarY - 8, progressBarWidth, 20);
        this.clickAreas.add(new ClickArea(progressBarX, progressBarY - 8, progressBarWidth, 20, () -> this.dragTarget = DragTarget.PROGRESS));

        // Volume — right side
        float volumeX = x + w - 120;
        float volumeValue = this.dragTarget == DragTarget.VOLUME && this.pendingVolume >= 0
                ? this.pendingVolume : player.getVolume();
        GlHelper.drawText("", volumeX - 18, timeY, ICON_FONT, 0xFF888888);
        drawSlider(ctx, volumeX, progressBarY, 80, volumeValue);
        this.lastVolumeRect = Rectangle.ofXYWH(volumeX, progressBarY - 8, 80, 20);
        this.clickAreas.add(new ClickArea(volumeX, progressBarY - 8, 80, 20, () -> this.dragTarget = DragTarget.VOLUME));
    }

    private void drawControlBtn(DrawContext ctx, float x, float y, float width, float height, String label,
                                int mx, int my, Runnable action) {
        boolean hover = contains(mx, my, x, y, width, height);
        rounded(ctx, x, y, width, height, 6, hover ? new int[]{255, 255, 255, 25} : new int[]{255, 255, 255, 10});
        float textWidth = GlHelper.getStringWidth(label, SMALL_FONT);
        GlHelper.drawText(label, x + (width - textWidth) / 2, y + (height - 14) / 2 + 8, SMALL_FONT, hover ? 0xFFFFFFFF : 0xFFCCCCCC);
        this.clickAreas.add(new ClickArea(x, y, width, height, action));
    }

    // --- Drawing Helpers ---

    private void drawGlowPanel(DrawContext ctx, float x, float y, float w, float h, float radius, float alpha) {
        ctx.drawRoundedRect(RoundedRectangle.ofXYWHR(x, y, w, h, radius),
                new Paint().setColor(ColorUtil.fromARGB(24, 24, 24, (int) (150 * alpha))));
        ctx.drawRoundedRect(RoundedRectangle.ofXYWHR(x, y, w, h, radius),
                new Paint().setColor(ColorUtil.fromARGB(255, 255, 255, (int) (35 * alpha))));
    }

    private void drawSlider(DrawContext ctx, float x, float y, float w, float value) {
        float p = clamp(value, 0, 1);
        rounded(ctx, x, y, w, 3, 1.5f, new int[]{255, 255, 255, 30});
        if (p > 0.001f) {
            rounded(ctx, x, y, w * p, 3, 1.5f, new int[]{255, 255, 255, 160});
        }
        rounded(ctx, x + w * p - 5, y - 3.5f, 10, 10, 5, new int[]{255, 255, 255, 220});
    }

    private void rounded(DrawContext ctx, float x, float y, float w, float h, float r, int[] rgba) {
        ctx.drawRoundedRect(RoundedRectangle.ofXYWHR(x, y, w, h, r),
                new Paint().setColor(ColorUtil.fromARGB(rgba[0], rgba[1], rgba[2], rgba[3])));
    }

    private float measure(String text, FontRenderer font) {
        return GlHelper.getStringWidth(text, font);
    }

    // --- Interaction ---

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        for (int i = this.clickAreas.size() - 1; i >= 0; i--) {
            ClickArea area = this.clickAreas.get(i);
            if (!area.contains(mouseX, mouseY)) continue;
            area.action.run();
            if (this.dragTarget == DragTarget.PROGRESS) {
                updateProgress(mouseX);
            } else if (this.dragTarget == DragTarget.VOLUME) {
                updateVolume(mouseX);
            }
            return true;
        }

        this.searchFocused = false;
        this.searchSelectAll = false;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && this.dragTarget == DragTarget.PROGRESS) {
            updateProgress(mouseX);
            return true;
        }
        if (button == 0 && this.dragTarget == DragTarget.VOLUME) {
            updateVolume(mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && this.dragTarget == DragTarget.VOLUME && this.pendingVolume >= 0) {
            MusicPlayer.AUDIO_PLAYER.setVolume(this.pendingVolume);
            this.pendingVolume = -1;
        }
        this.dragTarget = DragTarget.NONE;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (this.page == Page.SEARCH) {
            this.searchScroll = clamp(this.searchScroll - (float) delta * 28, 0, this.maxSearchScroll);
            return true;
        }
        if (this.page == Page.QUEUE) {
            this.queueScroll = clamp(this.queueScroll - (float) delta * 28, 0, this.maxQueueScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!this.searchFocused || codePoint < 32 || codePoint == 127) {
            return super.charTyped(codePoint, modifiers);
        }
        this.searchText = this.searchSelectAll ? String.valueOf(codePoint) : this.searchText + codePoint;
        this.searchSelectAll = false;
        this.searchDirty = true;
        this.lastSearchEditMs = System.currentTimeMillis();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.searchFocused) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                startSearch();
                return true;
            }
            if (Screen.isPaste(keyCode)) {
                String paste = Minecraft.getInstance().keyboardHandler.getClipboard();
                this.searchText = this.searchSelectAll ? paste : this.searchText + paste;
                this.searchSelectAll = false;
                this.searchDirty = true;
                this.lastSearchEditMs = System.currentTimeMillis();
                return true;
            }
            if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_A) {
                this.searchSelectAll = !this.searchText.isEmpty();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !this.searchText.isEmpty()) {
                if (this.searchSelectAll) {
                    this.searchText = "";
                    this.searchSelectAll = false;
                } else {
                    this.searchText = this.searchText.substring(0, this.searchText.length() - 1);
                }
                this.searchDirty = true;
                this.lastSearchEditMs = System.currentTimeMillis();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DELETE) {
                this.searchText = "";
                this.searchSelectAll = false;
                this.searchDirty = true;
                this.lastSearchEditMs = System.currentTimeMillis();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                this.searchFocused = false;
                return true;
            }
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // --- Actions ---

    private void startSearch() {
        this.searchDirty = false;
        String query = this.searchText.trim();
        long seq = this.searchSeq.incrementAndGet();
        if (query.isEmpty()) {
            this.searchResults = List.of();
            this.searching = false;
            return;
        }
        this.searching = true;
        NeteaseApi.search(query, 20).whenComplete((results, throwable) -> {
            if (this.searchSeq.get() != seq) return;
            this.searchResults = results == null ? List.of() : results;
            this.searching = false;
            this.searchScroll = 0;
        });
    }

    private void playSong(SongInfo song) {
        queueIndex = searchResults.indexOf(song);
        if (queueIndex >= 0) {
            playQueue.clear();
            playQueue.addAll(searchResults);
        }

        NeteaseApi.getLyrics(song.id).thenAccept(lyrics -> {
            this.lyricsCache.put(song.id, lyrics == null ? List.of() : lyrics);
            try {
                LyricsModule lyricsMod = NiloreClient.getInstance().getModuleManager().getModule(LyricsModule.class);
                if (lyricsMod != null) {
                    lyricsMod.setLyrics(song.id, lyrics);
                }
            } catch (Exception ignored) {}
        });

        NeteaseApi.getSongUrl(song.id).thenAccept(result -> {
            if (result == null) return;
            if (song.duration <= 0 && result.size() > 0) {
                song.duration = (result.size() * 1000L) / 40000;
            }
            waitForWarmupThenPlay(song, result.url());
        });
    }

    private void waitForWarmupThenPlay(SongInfo song, String url) {
        LyricsModule lyricsMod = NiloreClient.getInstance().getModuleManager().getModule(LyricsModule.class);
        if (lyricsMod == null || lyricsMod.isWarmupComplete()) {
            MusicPlayer.AUDIO_PLAYER.play(song, url);
            return;
        }
        new Thread(() -> {
            while (!lyricsMod.isWarmupComplete()) {
                try { Thread.sleep(50); } catch (InterruptedException e) { return; }
            }
            MusicPlayer.AUDIO_PLAYER.play(song, url);
        }, "LyricsWarmupWait").start();
    }

    private void nextSong() {
        if (playQueue.isEmpty() || queueIndex < 0) return;
        queueIndex = (queueIndex + 1) % playQueue.size();
        playSong(playQueue.get(queueIndex));
    }

    private void prevSong() {
        if (playQueue.isEmpty() || queueIndex < 0) return;
        queueIndex = (queueIndex - 1 + playQueue.size()) % playQueue.size();
        playSong(playQueue.get(queueIndex));
    }

    private void updateProgress(double mouseX) {
        if (this.lastProgressRect == null) return;
        AudioPlayer player = MusicPlayer.AUDIO_PLAYER;
        SongInfo song = player.getCurrentSong();
        if (song == null || song.duration <= 0) return;
        float ratio = (float) ((mouseX - this.lastProgressRect.getX()) / this.lastProgressRect.getWidth());
        ratio = clamp(ratio, 0, 1);
        player.seekToMs((long) (ratio * song.duration));
    }

    private void updateVolume(double mouseX) {
        if (this.lastVolumeRect == null) return;
        float ratio = (float) ((mouseX - this.lastVolumeRect.getX()) / this.lastVolumeRect.getWidth());
        this.pendingVolume = clamp(ratio, 0, 1);
        MusicPlayer.AUDIO_PLAYER.setVolume(this.pendingVolume);
        MusicPlayer musicMod = NiloreClient.getInstance().getModuleManager().getModule(MusicPlayer.class);
        if (musicMod != null) {
            musicMod.setVolumeSetting(this.pendingVolume);
        }
    }

    // --- Album Art ---

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
                    System.err.println("[MusicPlayerScreen] Album art too small, retrying...");
                    albumSongId = -1;
                }
            } catch (Exception e) {
                System.err.println("[MusicPlayerScreen] Failed to download album art: " + e.getMessage());
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

    // --- Utilities ---

    private static boolean contains(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private static String ellipsize(String value, int maxChars) {
        if (value == null) return "";
        if (value.length() <= maxChars) return value;
        return value.substring(0, Math.max(0, maxChars - 3)) + "...";
    }

    private static String timestamp(long ms) {
        long safe = Math.max(0, ms);
        long total = safe / 1000;
        return String.format(Locale.ROOT, "%02d:%02d", total / 60, total % 60);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // --- Types ---

    private enum Page {
        PLAYER, SEARCH, QUEUE, ABOUT
    }

    private enum DragTarget {
        NONE, PROGRESS, VOLUME
    }

    private record ClickArea(float x, float y, float width, float height, Runnable action) {
        boolean contains(double mouseX, double mouseY) {
            return MusicPlayerScreen.contains(mouseX, mouseY, this.x, this.y, this.width, this.height);
        }
    }
}
