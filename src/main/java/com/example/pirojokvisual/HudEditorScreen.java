package com.example.pirojokvisual;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class HudEditorScreen extends Screen {

    public HudEditorScreen() { super(Text.literal("HUD Editor")); }

    @Override
    protected void init() {
        HudDrag.editorMode = true;
    }

    @Override public boolean shouldPause() { return false; }

    @Override
    public void removed() {
        HudDrag.editorMode = false;
        HudDrag.dragging = null;
        PirojokVisual.save();
    }

    @Override
    public boolean keyPressed(int key, int scan, int mod) {
        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_H) { close(); return true; }
        return super.keyPressed(key, scan, mod);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && HudDrag.mouseClicked(mx, my)) return true;
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (HudDrag.mouseDragged(mx, my)) return true;
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        if (HudDrag.mouseReleased()) return true;
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        ctx.fill(0, 0, width, height, 0x88000000);

        for (String name : HudDrag.POS.keySet()) {
            if (PirojokVisual.isHudVisible(name)) {
                HudDrag.drawBox(ctx, name, PirojokVisual.themeColor());
            }
        }

        int accent = PirojokVisual.themeColor();
        ctx.drawTextWithShadow(textRenderer, "HUD EDITOR", width / 2 - textRenderer.getWidth("HUD EDITOR") / 2, 10, accent);
        ctx.drawTextWithShadow(textRenderer, "Drag items to move  |  H or ESC to close", width / 2 - textRenderer.getWidth("Drag items to move  |  H or ESC to close") / 2, height - 15, 0xFFFFFFFF);

        super.render(ctx, mx, my, delta);
    }
}
