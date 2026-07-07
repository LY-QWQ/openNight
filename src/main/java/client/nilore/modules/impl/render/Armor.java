package client.nilore.modules.impl.render;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import client.nilore.event.impl.GlRenderEvent;
import client.nilore.event.impl.Render2DEvent;
import client.nilore.hud.HudElement;
import client.nilore.render.DrawContext;
import client.nilore.render.Paint;
import client.nilore.render.RoundedRectangle;
import client.nilore.settings.impl.BooleanSetting;
import client.nilore.settings.impl.NumberSetting;
import client.nilore.utils.render.ColorUtil;

public class Armor
extends HudElement {
    private final BooleanSetting backgroundSetting = new BooleanSetting("Background", true);
    private final NumberSetting bgAlphaSetting = new NumberSetting("Bg Alpha", 70, 0, 255, 1);
    private final Paint bgPaint = new Paint();

    public Armor() {
        super("Armor");
        this.setX(10.0f);
        this.setY(120.0f);
        this.bgPaint.setAntialias(true);
    }

    @Override
    public void onRender2D(Render2DEvent render2DEvent, float x, float y) {
    }

    @Override
    public void onGlRender(GlRenderEvent glRenderEvent, float x, float y) {
        if (mc.player == null) {
            return;
        }
        DrawContext drawContext = glRenderEvent.drawContext();
        if (drawContext == null) {
            return;
        }
        float slotSize = 18.0f;
        float gap = 2.0f;
        float totalWidth = slotSize * 4.0f + gap * 3.0f;
        float totalHeight = slotSize;
        if (this.backgroundSetting.getValue()) {
            float padding = 3.0f;
            int alpha = this.bgAlphaSetting.getValue().intValue();
            this.bgPaint.setColor(ColorUtil.fromARGB(0, 0, 0, alpha));
            drawContext.drawRoundedRect(RoundedRectangle.ofXYWHR(x - padding, y - padding, totalWidth + padding * 2.0f, totalHeight + padding * 2.0f, 4.5f), this.bgPaint);
        }
        ItemStack[] armorItems = new ItemStack[]{mc.player.getItemBySlot(EquipmentSlot.HEAD), mc.player.getItemBySlot(EquipmentSlot.CHEST), mc.player.getItemBySlot(EquipmentSlot.LEGS), mc.player.getItemBySlot(EquipmentSlot.FEET)};
        for (int i = 0; i < 4; ++i) {
            float itemX = x + (float)i * (slotSize + gap);
            ItemStack itemStack = armorItems[i];
            if (itemStack.isEmpty()) continue;
            glRenderEvent.guiGraphics().renderItem(itemStack, (int)itemX, (int)y);
            glRenderEvent.guiGraphics().renderItemDecorations(mc.font, itemStack, (int)itemX, (int)y);
        }
        this.setWidth(totalWidth);
        this.setHeight(totalHeight);
    }

    @Override
    public void onSettings() {
    }
}