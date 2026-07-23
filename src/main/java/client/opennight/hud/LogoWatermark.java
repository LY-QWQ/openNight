package client.opennight.hud;

import client.opennight.ClientBase;
import client.opennight.NightClient;
import client.opennight.event.impl.GlRenderEvent;
import client.opennight.render.DrawContext;
import client.opennight.render.FontPresets;
import client.opennight.render.FontRenderer;

public class LogoWatermark {
    private static final String LOGO = "N";
    private static final FontRenderer LOGO_FONT = FontPresets.nightIcon(96.0f);

    public void onGlRender(GlRenderEvent event) {
        if (ClientBase.mc.player == null || NightClient.getInstance().getHudManager() == null) {
            return;
        }
        ModuleListHud moduleList = NightClient.getInstance().getHudManager().getHudElement(ModuleListHud.class);
        int topColor = moduleList == null ? 0xFFFFFFFF : moduleList.getThemeColor(0, 0.0f, 1);
        int bottomColor = moduleList == null ? 0xFFFFFFFF : moduleList.getThemeColor(1, 1.0f, 1);
        DrawContext drawContext = event.drawContext();
        drawContext.drawStringGradient(LOGO, -2.0f, 18.0f + LOGO_FONT.getMetrics().capHeight(), LOGO_FONT, topColor, bottomColor);
    }
}
