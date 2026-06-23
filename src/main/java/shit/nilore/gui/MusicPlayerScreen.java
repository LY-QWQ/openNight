package shit.nilore.gui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import shit.nilore.modules.impl.misc.MusicPlayer;
import shit.nilore.modules.impl.misc.music.AudioPlayer;
import shit.nilore.modules.impl.misc.music.NeteaseApi;
import shit.nilore.modules.impl.misc.music.SongInfo;
import shit.nilore.modules.impl.render.LyricsModule;
import shit.nilore.NiloreClient;
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
import org.lwjgl.glfw.GLFW;

public class MusicPlayerScreen extends Screen {
    // custom search input
    private String searchInput = "";
    private int cursorPos = 0;

    private final List<SongInfo> searchResults = new ArrayList<>();
    private final List<SongInfo> playQueue = new ArrayList<>();
    private int queueIndex = -1;
    private float scrollOffset = 0;
    private float scrollTarget = 0;
    private boolean isLoading = false;
    private String statusText = "";

    private final SmoothAnimationTimer openAnim = new SmoothAnimationTimer();
    private final SmoothAnimationTimer scrollAnim = new SmoothAnimationTimer();

    private static final float PANEL_W = 380;
    private static final float PANEL_H = 340;
    private static final float SEARCH_H = 32;
    private static final float ROW_H = 42;
    private static final float BOTTOM_H = 52;
    private static final float RADIUS = 14;
    private static final float PAD = 14;

    private static final int INPUT_BG = ColorUtil.fromARGB(255, 255, 255, 12);
    private static final int ROW_HOVER = ColorUtil.fromARGB(255, 255, 255, 10);
    private static final int ROW_PLAYING = ColorUtil.fromARGB(255, 255, 255, 15);
    private static final int DIVIDER = ColorUtil.fromARGB(255, 255, 255, 8);
    private static final int TEXT_PRIMARY = ColorUtil.fromARGB(255, 255, 255, 220);
    private static final int TEXT_SECONDARY = ColorUtil.fromARGB(255, 255, 255, 100);
    private static final int TEXT_DIM = ColorUtil.fromARGB(255, 255, 255, 60);
    private static final int ACCENT = ColorUtil.fromARGB(255, 255, 255, 200);
    private static final int WHITE = ColorUtil.fromARGB(255, 255, 255, 255);
    private static final int CURSOR_COLOR = ColorUtil.fromARGB(255, 255, 255, 180);

    private final FontRenderer titleFont = FontPresets.pingfang(17);
    private final FontRenderer subFont = FontPresets.pingfang(14);
    private final FontRenderer smallFont = FontPresets.pingfang(14);
    private final FontRenderer timeFont = FontPresets.pingfang(15);
    private final FontRenderer inputFont = FontPresets.pingfang(18);

    public MusicPlayerScreen() {
        super(Component.literal("Music Player"));
    }

    @Override
    protected void init() {
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        openAnim.animate(1.0, 0.3, Easings.EASE_OUT_QUAD);
        openAnim.tick();
        float openProgress = openAnim.getValueF();

        scrollAnim.animate(scrollTarget, 0.15, Easings.EASE_OUT_QUAD);
        scrollAnim.tick();
        scrollOffset = scrollAnim.getValueF();

        Renderer.render(guiGraphics, drawContext -> {
            float panelX = (this.width - PANEL_W) / 2f;
            float panelY = (this.height - PANEL_H) / 2f;

            float scale = 0.97f + 0.03f * openProgress;
            float centerX = this.width / 2f;
            float centerY = this.height / 2f;
            drawContext.save();
            drawContext.translate(centerX, centerY);
            drawContext.scale(scale, scale);
            drawContext.translate(-centerX, -centerY);

            // overlay
            drawContext.drawRectXYWH(0, 0, this.width, this.height,
                    new Paint().setColor(ColorUtil.fromARGB(0, 0, 0, (int)(60 * openProgress))));

            // panel
            drawContext.drawRoundedRect(
                    RoundedRectangle.ofXYWHR(panelX, panelY, PANEL_W, PANEL_H, RADIUS),
                    new Paint().setColor(ColorUtil.fromARGB(22, 22, 24, (int)(200 * openProgress))));

            float contentY = panelY + PAD;
            renderSearchBar(drawContext, panelX, contentY, mouseX, mouseY);
            contentY += SEARCH_H + 10;

            float listH = PANEL_H - SEARCH_H - BOTTOM_H - PAD * 3 - 10;
            renderSongList(drawContext, panelX + PAD, contentY, PANEL_W - PAD * 2, listH, mouseX, mouseY);

            float bottomY = panelY + PANEL_H - BOTTOM_H - PAD;
            drawContext.drawRectXYWH(panelX + PAD, bottomY - 1, PANEL_W - PAD * 2, 1,
                    new Paint().setColor(DIVIDER));

            renderBottomBar(drawContext, panelX + PAD, bottomY, PANEL_W - PAD * 2, mouseX, mouseY);

            if (isLoading) {
                String loading = "Loading...";
                float lw = GlHelper.getStringWidth(loading, subFont);
                GlHelper.drawText(loading, panelX + (PANEL_W - lw) / 2f, panelY + PANEL_H / 2f, subFont, TEXT_SECONDARY);
            }

            drawContext.restore();
        });
    }

    private void renderSearchBar(DrawContext ctx, float x, float y, int mouseX, int mouseY) {
        float inputW = PANEL_W - PAD * 2 - 42;
        float inputX = x + PAD;

        // input background
        ctx.drawRoundedRect(RoundedRectangle.ofXYWHR(inputX, y, inputW, SEARCH_H, 8), new Paint().setColor(INPUT_BG));

        // clip text inside input
        ctx.save();
        ctx.clipRect(Rectangle.ofXYWH(inputX + 10, y + 2, inputW - 20, SEARCH_H - 4), true);

        float textY = y + (SEARCH_H - 10) / 2f;

        if (searchInput.isEmpty()) {
            // placeholder
            GlHelper.drawText("Search songs...", inputX + 10, textY, inputFont, TEXT_DIM);
        } else {
            // input text
            GlHelper.drawText(searchInput, inputX + 10, textY, inputFont, TEXT_PRIMARY);

            // cursor
            String beforeCursor = searchInput.substring(0, cursorPos);
            float cursorX = inputX + 10 + GlHelper.getStringWidth(beforeCursor, inputFont);
            boolean cursorVisible = (System.currentTimeMillis() / 500) % 2 == 0;
            if (cursorVisible) {
                ctx.drawRectXYWH(cursorX, y + 6, 1, SEARCH_H - 12, new Paint().setColor(CURSOR_COLOR));
            }
        }

        ctx.restore();

        // → button
        float btnX = inputX + inputW + 6;
        boolean btnHover = isHovered(btnX, y, 30, SEARCH_H, mouseX, mouseY);
        GlHelper.drawText("→", btnX + 6, y + (SEARCH_H - 10) / 2f, titleFont,
                btnHover ? WHITE : TEXT_SECONDARY);
    }

    private void renderSongList(DrawContext ctx, float x, float y, float w, float h, int mouseX, int mouseY) {
        ctx.save();
        ctx.clipRect(Rectangle.ofXYWH(x, y, w, h), true);

        AudioPlayer player = MusicPlayer.AUDIO_PLAYER;
        SongInfo playing = player.getCurrentSong();

        float rowY = y - scrollOffset;
        for (int i = 0; i < searchResults.size(); i++) {
            SongInfo song = searchResults.get(i);
            if (rowY + ROW_H < y) { rowY += ROW_H; continue; }
            if (rowY > y + h) break;

            boolean isHover = isHovered(x, rowY, w, ROW_H, mouseX, mouseY);
            boolean isPlaying = playing != null && playing.id == song.id;

            if (isPlaying) {
                ctx.drawRoundedRect(RoundedRectangle.ofXYWHR(x, rowY, w, ROW_H, 6), new Paint().setColor(ROW_PLAYING));
            } else if (isHover) {
                ctx.drawRoundedRect(RoundedRectangle.ofXYWHR(x, rowY, w, ROW_H, 6), new Paint().setColor(ROW_HOVER));
            }

            if (i > 0) {
                ctx.drawRectXYWH(x + 12, rowY, w - 24, 1, new Paint().setColor(DIVIDER));
            }

            float textX = x + 14;
            float titleY = rowY + 7;

            GlHelper.drawText(song.name, textX, titleY, titleFont, isPlaying ? WHITE : TEXT_PRIMARY);
            GlHelper.drawText(song.artist, textX, titleY + 15, subFont, TEXT_SECONDARY);

            String dur = song.formatDuration();
            float durW = GlHelper.getStringWidth(dur, smallFont);
            GlHelper.drawText(dur, x + w - durW - 12, rowY + (ROW_H - 8) / 2f, smallFont, TEXT_DIM);

            rowY += ROW_H;
        }

        if (searchResults.isEmpty() && !isLoading) {
            String hint = "Search for songs";
            float hw = GlHelper.getStringWidth(hint, subFont);
            GlHelper.drawText(hint, x + (w - hw) / 2f, y + h / 2f - 4, subFont, TEXT_DIM);
        }

        ctx.restore();
    }

    private void renderBottomBar(DrawContext ctx, float x, float y, float w, int mouseX, int mouseY) {
        AudioPlayer player = MusicPlayer.AUDIO_PLAYER;
        SongInfo song = player.getCurrentSong();

        float ctrlSize = 28;
        float ctrlGap = 6;
        float totalCtrlW = ctrlSize * 3 + ctrlGap * 2;
        float ctrlStartX = x + (w - totalCtrlW) / 2f;
        float ctrlY = y + 2;

        boolean prevHover = isHovered(ctrlStartX, ctrlY, ctrlSize, ctrlSize, mouseX, mouseY);
        String prevLabel = "|<";
        float prevW = GlHelper.getStringWidth(prevLabel, smallFont);
        GlHelper.drawText(prevLabel, ctrlStartX + (ctrlSize - prevW) / 2f, ctrlY + 9, smallFont, prevHover ? WHITE : TEXT_SECONDARY);

        float playX = ctrlStartX + ctrlSize + ctrlGap;
        boolean playHover = isHovered(playX, ctrlY, ctrlSize, ctrlSize, mouseX, mouseY);
        String playLabel = player.getState() == AudioPlayer.State.PLAYING ? "||" : ">";
        float playW = GlHelper.getStringWidth(playLabel, smallFont);
        GlHelper.drawText(playLabel, playX + (ctrlSize - playW) / 2f, ctrlY + 9, smallFont, playHover ? WHITE : ACCENT);

        float nextX = playX + ctrlSize + ctrlGap;
        boolean nextHover = isHovered(nextX, ctrlY, ctrlSize, ctrlSize, mouseX, mouseY);
        String nextLabel = ">|";
        float nextW = GlHelper.getStringWidth(nextLabel, smallFont);
        GlHelper.drawText(nextLabel, nextX + (ctrlSize - nextW) / 2f, ctrlY + 9, smallFont, nextHover ? WHITE : TEXT_SECONDARY);

        if (song != null) {
            String info = song.name + "  -  " + song.artist;
            float infoW = GlHelper.getStringWidth(info, smallFont);
            float infoX = x + (w - Math.min(infoW, w - 20)) / 2f;
            ctx.save();
            ctx.clipRect(Rectangle.ofXYWH(x, ctrlY + ctrlSize, w, 14), true);
            GlHelper.drawText(info, infoX, ctrlY + ctrlSize + 1, smallFont, TEXT_DIM);
            ctx.restore();
        }

        float progY = y + BOTTOM_H - 14;
        float progW = w;
        float progH = 2;
        float progress = player.getProgress();

        ctx.drawRoundedRect(RoundedRectangle.ofXYWHR(x, progY, progW, progH, 1),
                new Paint().setColor(ColorUtil.fromARGB(255, 255, 255, 15)));

        if (progress > 0.001f) {
            ctx.drawRoundedRect(RoundedRectangle.ofXYWHR(x, progY, progW * progress, progH, 1),
                    new Paint().setColor(ColorUtil.fromARGB(255, 255, 255, 120)));
        }

        if (song != null) {
            String elapsed = formatMs(player.getCurrentPositionMs());
            String total = song.formatDuration();
            GlHelper.drawText(elapsed, x, progY + 5, timeFont, TEXT_DIM);
            float totalW = GlHelper.getStringWidth(total, timeFont);
            GlHelper.drawText(total, x + w - totalW, progY + 5, timeFont, TEXT_DIM);
        }
    }

    private String formatMs(long ms) {
        long sec = ms / 1000;
        return String.format("%d:%02d", sec / 60, sec % 60);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        float panelX = (this.width - PANEL_W) / 2f;
        float panelY = (this.height - PANEL_H) / 2f;

        // search input click — move cursor
        float inputX = panelX + PAD;
        float inputW = PANEL_W - PAD * 2 - 42;
        float inputY = panelY + PAD;
        if (isHovered(inputX, inputY, inputW, SEARCH_H, mouseX, mouseY)) {
            float relativeX = (float) mouseX - inputX - 10;
            cursorPos = findCursorPos(searchInput, relativeX);
            return true;
        }

        // search button click
        float btnX = inputX + inputW + 6;
        if (isHovered(btnX, inputY, 30, SEARCH_H, mouseX, mouseY)) {
            performSearch(searchInput);
            return true;
        }

        // song list click
        float listY = panelY + PAD + SEARCH_H + 10;
        float listH = PANEL_H - SEARCH_H - BOTTOM_H - PAD * 3 - 10;
        float listX = panelX + PAD;
        float listW = PANEL_W - PAD * 2;
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
        float bottomY = panelY + PANEL_H - BOTTOM_H - PAD;
        float ctrlSize = 28;
        float ctrlGap = 6;
        float totalCtrlW = ctrlSize * 3 + ctrlGap * 2;
        float ctrlStartX = panelX + PAD + (PANEL_W - PAD * 2 - totalCtrlW) / 2f;
        float ctrlY = bottomY + 2;

        if (isHovered(ctrlStartX, ctrlY, ctrlSize, ctrlSize, mouseX, mouseY)) {
            prevSong();
            return true;
        }
        float playX = ctrlStartX + ctrlSize + ctrlGap;
        if (isHovered(playX, ctrlY, ctrlSize, ctrlSize, mouseX, mouseY)) {
            MusicPlayer.AUDIO_PLAYER.togglePause();
            return true;
        }
        float nextX = playX + ctrlSize + ctrlGap;
        if (isHovered(nextX, ctrlY, ctrlSize, ctrlSize, mouseX, mouseY)) {
            nextSong();
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        float listH = PANEL_H - SEARCH_H - BOTTOM_H - PAD * 3 - 10;
        float maxScroll = Math.max(0, searchResults.size() * ROW_H - listH);
        scrollTarget = (float)Math.max(0, Math.min(maxScroll, scrollTarget - delta * 3));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER) {
            performSearch(searchInput);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (cursorPos > 0 && !searchInput.isEmpty()) {
                searchInput = searchInput.substring(0, cursorPos - 1) + searchInput.substring(cursorPos);
                cursorPos--;
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            if (cursorPos < searchInput.length()) {
                searchInput = searchInput.substring(0, cursorPos) + searchInput.substring(cursorPos + 1);
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            cursorPos = Math.max(0, cursorPos - 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            cursorPos = Math.min(searchInput.length(), cursorPos + 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_HOME) {
            cursorPos = 0;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_END) {
            cursorPos = searchInput.length();
            return true;
        }
        // Ctrl+A select all (just move cursor to end)
        if (keyCode == GLFW.GLFW_KEY_A && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            cursorPos = searchInput.length();
            return true;
        }
        // Ctrl+V paste
        if (keyCode == GLFW.GLFW_KEY_V && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
            if (clipboard != null && !clipboard.isEmpty()) {
                searchInput = searchInput.substring(0, cursorPos) + clipboard + searchInput.substring(cursorPos);
                cursorPos += clipboard.length();
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (codePoint >= 32 && searchInput.length() < 100) {
            searchInput = searchInput.substring(0, cursorPos) + codePoint + searchInput.substring(cursorPos);
            cursorPos++;
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    private int findCursorPos(String text, float relativeX) {
        if (relativeX <= 0 || text.isEmpty()) return 0;
        for (int i = 1; i <= text.length(); i++) {
            float w = GlHelper.getStringWidth(text.substring(0, i), inputFont);
            if (w >= relativeX) {
                float prevW = GlHelper.getStringWidth(text.substring(0, i - 1), inputFont);
                return (relativeX - prevW < w - relativeX) ? i - 1 : i;
            }
        }
        return text.length();
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

        // preload lyrics
        NeteaseApi.getLyrics(song.id).thenAccept(lyrics -> {
            try {
                LyricsModule lyricsMod = NiloreClient.getInstance().getModuleManager().getModule(LyricsModule.class);
                if (lyricsMod != null) {
                    lyricsMod.setLyrics(song.id, lyrics);
                }
            } catch (Exception ignored) {}
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
