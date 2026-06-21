package shit.nilore.gui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import shit.nilore.modules.impl.misc.MusicPlayer;
import shit.nilore.modules.impl.misc.music.AudioPlayer;
import shit.nilore.modules.impl.misc.music.NeteaseApi;
import shit.nilore.modules.impl.misc.music.SongInfo;
import shit.nilore.render.DrawContext;
import shit.nilore.render.FontPresets;
import shit.nilore.render.FontRenderer;
import shit.nilore.render.GlHelper;
import shit.nilore.render.Paint;
import shit.nilore.render.Rectangle;
import shit.nilore.render.Renderer;
import shit.nilore.render.RoundedRectangle;
import shit.nilore.utils.animation.SmoothAnimationTimer;
import shit.nilore.utils.math.Easings;
import shit.nilore.utils.render.ColorUtil;

public class MusicPlayerScreen extends Screen {
    private EditBox searchBox;
    private final List<SongInfo> searchResults = new ArrayList<>();
    private final List<SongInfo> playQueue = new ArrayList<>();
    private int queueIndex = -1;
    private float scrollOffset = 0;
    private float scrollTarget = 0;
    private boolean isLoading = false;
    private String statusText = "";

    private final SmoothAnimationTimer openAnim = new SmoothAnimationTimer();
    private final SmoothAnimationTimer scrollAnim = new SmoothAnimationTimer();

    private static final float PANEL_W = 400;
    private static final float PANEL_H = 320;
    private static final float SEARCH_H = 28;
    private static final float ROW_H = 36;
    private static final float BOTTOM_H = 44;
    private static final float RADIUS = 10;
    private static final float PADDING = 8;

    // dark palette — matches other HUDs (black bg, white text, semi-transparent)
    private static final int ROW_BG = ColorUtil.fromARGB(0, 0, 0, 60);
    private static final int ROW_HOVER = ColorUtil.fromARGB(0, 0, 0, 100);
    private static final int ROW_PLAYING = ColorUtil.fromARGB(0, 0, 0, 120);
    private static final int ACCENT = ColorUtil.fromARGB(255, 255, 255, 255);
    private static final int TEXT_PRIMARY = ColorUtil.fromARGB(255, 255, 255, 255);
    private static final int TEXT_SECONDARY = ColorUtil.fromARGB(255, 255, 255, 140);
    private static final int TEXT_DIM = ColorUtil.fromARGB(255, 255, 255, 100);
    private static final int TEXT_BLACK = ColorUtil.fromARGB(0, 0, 0, 255);

    private final FontRenderer titleFont = FontPresets.axiformaBold(13);
    private final FontRenderer subFont = FontPresets.axiformaRegular(11);
    private final FontRenderer smallFont = FontPresets.axiformaRegular(10);

    public MusicPlayerScreen() {
        super(Component.literal("Music Player"));
    }

    @Override
    protected void init() {
        float panelX = (this.width - PANEL_W) / 2f;
        float panelY = (this.height - PANEL_H) / 2f;
        searchBox = new EditBox(this.font, (int)(panelX + PADDING + 2), (int)(panelY + PADDING + 2),
                (int)(PANEL_W - PADDING * 2 - 4 - 60), (int)(SEARCH_H - 4), Component.literal("Search"));
        searchBox.setMaxLength(100);
        searchBox.setResponder(null);
        addWidget(searchBox);
        setInitialFocus(searchBox);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        openAnim.animate(1.0, 0.25, Easings.EASE_OUT_POW3);
        openAnim.tick();
        float openProgress = openAnim.getValueF();

        scrollAnim.animate(scrollTarget, 0.15, Easings.EASE_OUT_QUAD);
        scrollAnim.tick();
        scrollOffset = scrollAnim.getValueF();

        Renderer.render(guiGraphics, drawContext -> {
            float panelX = (this.width - PANEL_W) / 2f;
            float panelY = (this.height - PANEL_H) / 2f;

            float scale = 0.95f + 0.05f * openProgress;
            float centerX = this.width / 2f;
            float centerY = this.height / 2f;
            drawContext.save();
            drawContext.translate(centerX, centerY);
            drawContext.scale(scale, scale);
            drawContext.translate(-centerX, -centerY);

            int overlayAlpha = (int)(100 * openProgress);
            Paint overlayPaint = new Paint().setColor(ColorUtil.fromARGB(0, 0, 0, overlayAlpha));
            drawContext.drawRectXYWH(0, 0, this.width, this.height, overlayPaint);

            int panelAlpha = (int)(180 * openProgress);
            Paint panelPaint = new Paint().setColor(ColorUtil.fromARGB(20, 20, 22, panelAlpha));
            drawContext.drawRoundedRect(RoundedRectangle.ofXYWHR(panelX, panelY, PANEL_W, PANEL_H, RADIUS), panelPaint);

            float contentY = panelY + PADDING;
            renderSearchBar(drawContext, panelX, contentY, mouseX, mouseY);
            contentY += SEARCH_H + 4;

            float listH = PANEL_H - SEARCH_H - BOTTOM_H - PADDING * 3 - 4;
            renderSongList(drawContext, panelX + PADDING, contentY, PANEL_W - PADDING * 2, listH, mouseX, mouseY);

            float bottomY = panelY + PANEL_H - BOTTOM_H - PADDING;
            renderBottomBar(drawContext, panelX + PADDING, bottomY, PANEL_W - PADDING * 2, mouseX, mouseY);

            if (isLoading) {
                FontRenderer loadFont = FontPresets.axiformaBold(12);
                String loading = "Loading...";
                float lw = GlHelper.getStringWidth(loading, loadFont);
                GlHelper.drawText(loading, panelX + (PANEL_W - lw) / 2f, panelY + PANEL_H / 2f, loadFont, TEXT_SECONDARY);
            }

            if (!statusText.isEmpty()) {
                GlHelper.drawText(statusText, panelX + PADDING, panelY + PANEL_H - PADDING - 4, smallFont, TEXT_DIM);
            }

            drawContext.restore();
        });

        searchBox.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderSearchBar(DrawContext ctx, float x, float y, int mouseX, int mouseY) {
        float barW = PANEL_W - PADDING * 2 - 56;
        Paint bgPaint = new Paint().setColor(ColorUtil.fromARGB(0, 0, 0, 80));
        ctx.drawRoundedRect(RoundedRectangle.ofXYWHR(x + PADDING, y, barW, SEARCH_H, 6), bgPaint);

        float btnX = x + PADDING + barW + 6;
        boolean btnHover = isHovered(btnX, y, 46, SEARCH_H, mouseX, mouseY);
        Paint btnPaint = new Paint().setColor(btnHover ? ColorUtil.fromARGB(200, 200, 205, 255) : ACCENT);
        ctx.drawRoundedRect(RoundedRectangle.ofXYWHR(btnX, y, 46, SEARCH_H, 6), btnPaint);
        String searchLabel = "Go";
        float labelW = GlHelper.getStringWidth(searchLabel, smallFont);
        GlHelper.drawText(searchLabel, btnX + (46 - labelW) / 2f, y + (SEARCH_H - 8) / 2f, smallFont, TEXT_BLACK);
    }

    private void renderSongList(DrawContext ctx, float x, float y, float w, float h, int mouseX, int mouseY) {
        ctx.save();
        ctx.clipRect(Rectangle.ofXYWH(x, y, w, h), true);

        Paint listBg = new Paint().setColor(ColorUtil.fromARGB(0, 0, 0, 40));
        ctx.drawRoundedRect(RoundedRectangle.ofXYWHR(x, y, w, h, 6), listBg);

        AudioPlayer player = MusicPlayer.AUDIO_PLAYER;
        SongInfo playing = player.getCurrentSong();

        float rowY = y - scrollOffset;
        for (int i = 0; i < searchResults.size(); i++) {
            SongInfo song = searchResults.get(i);
            if (rowY + ROW_H < y) { rowY += ROW_H; continue; }
            if (rowY > y + h) break;

            boolean isHover = isHovered(x, rowY, w, ROW_H, mouseX, mouseY);
            boolean isPlaying = playing != null && playing.id == song.id;

            int bgColor;
            if (isPlaying) bgColor = ROW_PLAYING;
            else if (isHover) bgColor = ROW_HOVER;
            else bgColor = ROW_BG;
            ctx.drawRoundedRect(RoundedRectangle.ofXYWHR(x + 2, rowY + 1, w - 4, ROW_H - 2, 4), new Paint().setColor(bgColor));

            if (isPlaying) {
                Paint accentBar = new Paint().setColor(ACCENT);
                ctx.drawRoundedRect(RoundedRectangle.ofXYWHR(x + 2, rowY + 1, 2, ROW_H - 2, 1), accentBar);
            }

            float textX = x + 10;
            float titleY = rowY + 5;
            GlHelper.drawText(song.name, textX, titleY, titleFont, TEXT_PRIMARY);
            GlHelper.drawText(song.artist, textX, titleY + 13, subFont, TEXT_SECONDARY);

            String dur = song.formatDuration();
            float durW = GlHelper.getStringWidth(dur, smallFont);
            GlHelper.drawText(dur, x + w - durW - 8, rowY + (ROW_H - 8) / 2f, smallFont, TEXT_DIM);

            rowY += ROW_H;
        }

        if (searchResults.isEmpty() && !isLoading) {
            String hint = "Search for songs above";
            float hw = GlHelper.getStringWidth(hint, subFont);
            GlHelper.drawText(hint, x + (w - hw) / 2f, y + h / 2f - 4, subFont, TEXT_DIM);
        }

        ctx.restore();
    }

    private void renderBottomBar(DrawContext ctx, float x, float y, float w, int mouseX, int mouseY) {
        AudioPlayer player = MusicPlayer.AUDIO_PLAYER;
        SongInfo song = player.getCurrentSong();

        Paint barBg = new Paint().setColor(ColorUtil.fromARGB(0, 0, 0, 60));
        ctx.drawRoundedRect(RoundedRectangle.ofXYWHR(x, y, w, BOTTOM_H, 6), barBg);

        float ctrlX = x + 8;
        float ctrlY = y + 4;

        // prev button
        boolean prevHover = isHovered(ctrlX, ctrlY, 20, 20, mouseX, mouseY);
        String prevLabel = "<<";
        float prevW = GlHelper.getStringWidth(prevLabel, smallFont);
        GlHelper.drawText(prevLabel, ctrlX + (20 - prevW) / 2f, ctrlY + 6, smallFont, prevHover ? TEXT_PRIMARY : TEXT_SECONDARY);
        ctrlX += 24;

        // play/pause button
        boolean playHover = isHovered(ctrlX, ctrlY, 20, 20, mouseX, mouseY);
        String playLabel = player.getState() == AudioPlayer.State.PLAYING ? "||" : ">";
        float playW = GlHelper.getStringWidth(playLabel, smallFont);
        GlHelper.drawText(playLabel, ctrlX + (20 - playW) / 2f, ctrlY + 6, smallFont, playHover ? TEXT_PRIMARY : TEXT_SECONDARY);
        ctrlX += 24;

        // next button
        boolean nextHover = isHovered(ctrlX, ctrlY, 20, 20, mouseX, mouseY);
        String nextLabel = ">>";
        float nextW = GlHelper.getStringWidth(nextLabel, smallFont);
        GlHelper.drawText(nextLabel, ctrlX + (20 - nextW) / 2f, ctrlY + 6, smallFont, nextHover ? TEXT_PRIMARY : TEXT_SECONDARY);
        ctrlX += 28;

        // song info
        float infoX = ctrlX;
        if (song != null) {
            GlHelper.drawText(song.name, infoX, y + 6, smallFont, TEXT_PRIMARY);
            GlHelper.drawText(song.artist, infoX, y + 17, smallFont, TEXT_SECONDARY);
        } else {
            GlHelper.drawText("No song playing", infoX, y + 10, smallFont, TEXT_DIM);
        }

        // progress bar
        float progY = y + BOTTOM_H - 10;
        float progW = w - 16;
        float progH = 3;
        float progress = player.getProgress();
        ctx.drawRoundedRect(RoundedRectangle.ofXYWHR(x + 8, progY, progW, progH, 1.5f),
                new Paint().setColor(ColorUtil.fromARGB(255, 255, 255, 30)));

        if (progress > 0.001f) {
            ctx.drawRoundedRect(RoundedRectangle.ofXYWHR(x + 8, progY, progW * progress, progH, 1.5f),
                    new Paint().setColor(ACCENT));
        }

        if (song != null) {
            String elapsed = formatMs(player.getCurrentPositionMs());
            String total = song.formatDuration();
            GlHelper.drawText(elapsed, x + 8, progY + 6, smallFont, TEXT_DIM);
            float totalW = GlHelper.getStringWidth(total, smallFont);
            GlHelper.drawText(total, x + w - totalW - 8, progY + 6, smallFont, TEXT_DIM);
        }
    }

    private String formatMs(long ms) {
        long sec = ms / 1000;
        return String.format("%d:%02d", sec / 60, sec % 60);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (searchBox.mouseClicked(mouseX, mouseY, button)) return true;

        float panelX = (this.width - PANEL_W) / 2f;
        float panelY = (this.height - PANEL_H) / 2f;

        // search button click
        float barW = PANEL_W - PADDING * 2 - 56;
        float btnX = panelX + PADDING + barW + 6;
        float btnY = panelY + PADDING;
        if (isHovered(btnX, btnY, 46, SEARCH_H, mouseX, mouseY)) {
            performSearch(searchBox.getValue());
            return true;
        }

        // song list click
        float listY = panelY + PADDING + SEARCH_H + 4;
        float listH = PANEL_H - SEARCH_H - BOTTOM_H - PADDING * 3 - 4;
        float listX = panelX + PADDING;
        float listW = PANEL_W - PADDING * 2;
        if (isHovered(listX, listY, listW, listH, mouseX, mouseY)) {
            float rowY = listY - scrollOffset;
            for (int i = 0; i < searchResults.size(); i++) {
                if (isHovered(listX, rowY, listW, ROW_H, mouseX, mouseY)) {
                    playSong(searchResults.get(i));
                    return true;
                }
                rowY += ROW_H;
            }
        }

        // bottom bar controls
        float bottomY = panelY + PANEL_H - BOTTOM_H - PADDING;
        float ctrlX = panelX + PADDING + 8;
        float ctrlY = bottomY + 4;

        // prev
        if (isHovered(ctrlX, ctrlY, 20, 20, mouseX, mouseY)) {
            prevSong();
            return true;
        }
        ctrlX += 24;
        // play/pause
        if (isHovered(ctrlX, ctrlY, 20, 20, mouseX, mouseY)) {
            MusicPlayer.AUDIO_PLAYER.togglePause();
            return true;
        }
        ctrlX += 24;
        // next
        if (isHovered(ctrlX, ctrlY, 20, 20, mouseX, mouseY)) {
            nextSong();
            return true;
        }


        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        float listH = PANEL_H - SEARCH_H - BOTTOM_H - PADDING * 3 - 4;
        float maxScroll = Math.max(0, searchResults.size() * ROW_H - listH);
        scrollTarget = (float)Math.max(0, Math.min(maxScroll, scrollTarget - delta * 3));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESC
            this.onClose();
            return true;
        }
        if (keyCode == 257) { // Enter
            performSearch(searchBox.getValue());
            return true;
        }
        if (searchBox.isFocused()) {
            return searchBox.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (searchBox.isFocused()) {
            return searchBox.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void performSearch(String query) {
        if (query == null || query.trim().isEmpty()) return;
        isLoading = true;
        statusText = "Searching...";
        NeteaseApi.search(query.trim(), 20).thenAccept(results -> {
            searchResults.clear();
            searchResults.addAll(results);
            scrollTarget = 0;
            scrollOffset = 0;
            isLoading = false;
            statusText = results.isEmpty() ? "No results found" : results.size() + " songs found";
        }).exceptionally(e -> {
            isLoading = false;
            statusText = "Search failed: " + e.getMessage();
            return null;
        });
    }

    private void playSong(SongInfo song) {
        statusText = "Loading: " + song.name + " - " + song.artist;
        queueIndex = searchResults.indexOf(song);
        playQueue.clear();
        playQueue.addAll(searchResults);
        NeteaseApi.getSongUrl(song.id).thenAccept(url -> {
            if (url != null) {
                MusicPlayer.AUDIO_PLAYER.play(song, url);
                statusText = "";
            } else {
                statusText = "Song unavailable (VIP or region locked)";
            }
        }).exceptionally(e -> {
            statusText = "Failed to get song URL";
            return null;
        });
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

    private boolean isHovered(double x, double y, double w, double h, double mx, double my) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
