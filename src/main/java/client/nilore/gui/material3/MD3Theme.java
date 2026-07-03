package client.nilore.gui.material3;

import java.util.HashMap;
import java.util.Map;
import client.nilore.modules.Category;
import client.nilore.render.FontPresets;
import client.nilore.render.FontRenderer;
import client.nilore.render.GlHelper;

public final class MD3Theme {

    public static int SCRIM             = 0xCC090B10;
    public static int SURFACE           = 0xFF101318;
    public static int SURFACE_DIM       = 0xFF151A20;
    public static int SIDEBAR           = 0xFF171D24;
    public static int SURFACE_CONTAINER = 0xFF1D232C;
    public static int SURFACE_HIGH      = 0xFF242B35;
    public static int SURFACE_HIGHEST   = 0xFF2D3541;

    public static int PRIMARY           = 0xFF8BE7FF;
    public static int PRIMARY_CONTAINER = 0xFF1D4450;
    public static int ON_PRIMARY        = 0xFF071521;

    public static int SECONDARY         = 0xFFC1D3E8;
    public static int SECONDARY_CONTAINER = 0xFF2A3440;

    public static int TEXT_HIGH         = 0xFFF2F5F8;
    public static int TEXT_MED          = 0xFFCBD3DC;
    public static int TEXT_LOW          = 0xFF8F98A3;
    public static int TEXT_DISABLED     = 0xFF5A6470;

    public static int OUTLINE           = 0xFF33404D;
    public static int OUTLINE_VARIANT   = 0xFF27313C;

    private static final Map<Category, int[]> CAT_DARK = new HashMap<>();
    private static final Map<Category, int[]> CAT_LIGHT = new HashMap<>();
    private static Map<Category, int[]> activeCat = CAT_DARK;
    static {
        CAT_DARK.put(Category.COMBAT,   new int[]{0xFFFF6B7A, 0xFF4B2028});
        CAT_DARK.put(Category.MOVEMENT, new int[]{0xFF5ED7FF, 0xFF173D4B});
        CAT_DARK.put(Category.PLAYER,   new int[]{0xFF7DE3A1, 0xFF1C3F2C});
        CAT_DARK.put(Category.RENDER,   new int[]{0xFFC69BFF, 0xFF372650});
        CAT_DARK.put(Category.EXPLOIT,  new int[]{0xFFFFC857, 0xFF493816});
        CAT_DARK.put(Category.WORLD,    new int[]{0xFF55DEC8, 0xFF153F3A});
        CAT_DARK.put(Category.MISC,     new int[]{0xFFA8B7D5, 0xFF273141});

        CAT_LIGHT.put(Category.COMBAT,   new int[]{0xFFC93445, 0xFFFFDADD});
        CAT_LIGHT.put(Category.MOVEMENT, new int[]{0xFF0079A5, 0xFFD1F0FF});
        CAT_LIGHT.put(Category.PLAYER,   new int[]{0xFF168449, 0xFFD6F8E0});
        CAT_LIGHT.put(Category.RENDER,   new int[]{0xFF7650C8, 0xFFEADCFF});
        CAT_LIGHT.put(Category.EXPLOIT,  new int[]{0xFF9A6A00, 0xFFFFE7B3});
        CAT_LIGHT.put(Category.WORLD,    new int[]{0xFF00816F, 0xFFC8F3EC});
        CAT_LIGHT.put(Category.MISC,     new int[]{0xFF5F6F8D, 0xFFE1E8F8});
    }

    // Material Icons
    private static final Map<Category, String> ICONS = new HashMap<>();
    static {
        ICONS.put(Category.COMBAT,   "");
        ICONS.put(Category.MOVEMENT, "");
        ICONS.put(Category.PLAYER,   "");
        ICONS.put(Category.RENDER,   "");
        ICONS.put(Category.EXPLOIT,  "");
        ICONS.put(Category.WORLD,    "");
        ICONS.put(Category.MISC,     "");
    }

    // Material icon codepoints for special glyphs
    public static final String ICON_CLOSE   = "";
    public static final String ICON_PERSON  = "";
    public static final String ICON_MODULE  = "";

    private static final Map<Category, String> LABELS = new HashMap<>();
    static {
        LABELS.put(Category.COMBAT,   "Combat");
        LABELS.put(Category.MOVEMENT, "Movement");
        LABELS.put(Category.PLAYER,   "Player");
        LABELS.put(Category.RENDER,   "Render");
        LABELS.put(Category.EXPLOIT,  "Exploit");
        LABELS.put(Category.WORLD,    "World");
        LABELS.put(Category.MISC,     "Ghost");
    }

    private MD3Theme() {}

    // ── Fonts ──
    public static FontRenderer fontDisplay(float s)     { return FontPresets.axiformaExtraBold(24 * s); }
    public static FontRenderer fontHeadline(float s)    { return FontPresets.axiformaBold(18 * s); }
    public static FontRenderer fontTitle(float s)       { return FontPresets.axiformaBold(15 * s); }
    public static FontRenderer fontTitleMedium(float s) { return FontPresets.axiformaBold(13 * s); }
    public static FontRenderer fontBody(float s)        { return FontPresets.axiformaRegular(13 * s); }
    public static FontRenderer fontBodyLarge(float s)   { return FontPresets.axiformaRegular(14 * s); }
    public static FontRenderer fontLabel(float s)       { return FontPresets.axiformaRegular(11 * s); }
    public static FontRenderer fontLabelLarge(float s)  { return FontPresets.axiformaRegular(12 * s); }
    public static FontRenderer fontMaterial(float sz)   { return FontPresets.materialIcons(sz); }

    // ── Category ──
    public static String icon(Category c)    { return ICONS.getOrDefault(c, ""); }
    public static String label(Category c)   { return LABELS.getOrDefault(c, c.displayName); }
    public static int primary(Category c)    { int[] v = activeCat.get(c); return v != null ? v[0] : PRIMARY; }
    public static int container(Category c)  { int[] v = activeCat.get(c); return v != null ? v[1] : PRIMARY_CONTAINER; }

    public static void useLight(boolean light) {
        if (light) {
            SCRIM = 0x660A0D12;
            SURFACE = 0xFFF8FAFD;
            SURFACE_DIM = 0xFFE8EDF3;
            SIDEBAR = 0xFFF0F4FA;
            SURFACE_CONTAINER = 0xFFEFF4FA;
            SURFACE_HIGH = 0xFFE2E8F0;
            SURFACE_HIGHEST = 0xFFD4DCE7;
            PRIMARY = 0xFF0079A5;
            PRIMARY_CONTAINER = 0xFFD1F0FF;
            ON_PRIMARY = 0xFFFFFFFF;
            SECONDARY = 0xFF526170;
            SECONDARY_CONTAINER = 0xFFD7E3F0;
            TEXT_HIGH = 0xFF12161D;
            TEXT_MED = 0xFF3D4855;
            TEXT_LOW = 0xFF6B7684;
            TEXT_DISABLED = 0xFF9AA4B0;
            OUTLINE = 0xFFB9C3CF;
            OUTLINE_VARIANT = 0xFFD1D9E3;
            activeCat = CAT_LIGHT;
        } else {
            SCRIM = 0xCC090B10;
            SURFACE = 0xFF101318;
            SURFACE_DIM = 0xFF151A20;
            SIDEBAR = 0xFF171D24;
            SURFACE_CONTAINER = 0xFF1D232C;
            SURFACE_HIGH = 0xFF242B35;
            SURFACE_HIGHEST = 0xFF2D3541;
            PRIMARY = 0xFF5ED7FF;
            PRIMARY_CONTAINER = 0xFF173D4B;
            ON_PRIMARY = 0xFF071521;
            SECONDARY = 0xFFC1D3E8;
            SECONDARY_CONTAINER = 0xFF2A3440;
            TEXT_HIGH = 0xFFF2F5F8;
            TEXT_MED = 0xFFCBD3DC;
            TEXT_LOW = 0xFF8F98A3;
            TEXT_DISABLED = 0xFF5A6470;
            OUTLINE = 0xFF33404D;
            OUTLINE_VARIANT = 0xFF27313C;
            activeCat = CAT_DARK;
        }
    }

    // ── Color helpers ──
    public static int withAlpha(int c, float a) {
        return ((int)(((c >> 24) & 0xFF) * a) << 24) | (c & 0x00FFFFFF);
    }

    public static int argb(int a, int r, int g, int b) {
        return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    public static int lerpColor(int from, int to, float t) {
        float inv = 1f - t;
        int a = (int)(((from >> 24) & 0xFF) * inv + ((to >> 24) & 0xFF) * t);
        int r = (int)(((from >> 16) & 0xFF) * inv + ((to >> 16) & 0xFF) * t);
        int g = (int)(((from >> 8) & 0xFF) * inv + ((to >> 8) & 0xFF) * t);
        int b = (int)((from & 0xFF) * inv + (to & 0xFF) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static int brighten(int c, float f) {
        int a = (c >> 24) & 0xFF;
        int r = Math.min(255, (int)(((c >> 16) & 0xFF) * f));
        int g = Math.min(255, (int)(((c >> 8) & 0xFF) * f));
        int b = Math.min(255, (int)((c & 0xFF) * f));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static void text(String t, float x, float y, FontRenderer f, int c, float a) {
        GlHelper.drawText(t, x, y, f, withAlpha(c, a));
    }
}
