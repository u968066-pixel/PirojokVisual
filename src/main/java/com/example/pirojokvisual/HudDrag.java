package com.example.pirojokvisual;

import net.minecraft.client.gui.DrawContext;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class HudDrag {

    public static final Map<String, int[]> POS = new HashMap<>();
    public static final Map<String, int[]> DEFAULT = new HashMap<>();
    public static boolean editorMode = false;
    public static String dragging = null;
    public static int dragDX, dragDY;

    public static void register(String name, int x, int y, int w, int h) {
        int[] d = {x, y, w, h};
        DEFAULT.put(name, d);
        if (!POS.containsKey(name)) POS.put(name, new int[]{x, y, w, h});
    }

    public static int[] pos(String name) {
        int[] p = POS.get(name);
        if (p == null) {
            int[] d = DEFAULT.get(name);
            if (d == null) return new int[]{0, 0, 10, 10};
            p = new int[]{d[0], d[1], d[2], d[3]};
            POS.put(name, p);
        }
        return p;
    }

    public static void drawBox(DrawContext ctx, String name, int color) {
        int[] p = pos(name);
        ctx.fill(p[0] - 1, p[1] - 1, p[0] + p[2] + 1, p[1], color);
        ctx.fill(p[0] - 1, p[1] + p[3], p[0] + p[2] + 1, p[1] + p[3] + 1, color);
        ctx.fill(p[0] - 1, p[1] - 1, p[0], p[1] + p[3] + 1, color);
        ctx.fill(p[0] + p[2], p[1] - 1, p[0] + p[2] + 1, p[1] + p[3] + 1, color);
        ctx.fill(p[0], p[1], p[0] + p[2], p[1] + p[3], 0x30FFFFFF);
    }

    public static boolean mouseClicked(double mx, double my) {
        if (!editorMode) return false;
        for (Map.Entry<String, int[]> e : POS.entrySet()) {
            int[] p = e.getValue();
            if (mx >= p[0] && mx <= p[0] + p[2] && my >= p[1] && my <= p[1] + p[3]) {
                dragging = e.getKey();
                dragDX = (int) mx - p[0];
                dragDY = (int) my - p[1];
                return true;
            }
        }
        return false;
    }

    public static boolean mouseDragged(double mx, double my) {
        if (dragging == null) return false;
        int[] p = POS.get(dragging);
        if (p == null) return false;
        p[0] = (int) mx - dragDX;
        p[1] = (int) my - dragDY;
        return true;
    }

    public static boolean mouseReleased() {
        if (dragging != null) {
            dragging = null;
            PirojokVisual.save();
            return true;
        }
        return false;
    }

    public static void saveAll(Properties p) {
        for (Map.Entry<String, int[]> e : POS.entrySet()) {
            int[] v = e.getValue();
            p.setProperty("hud." + PirojokVisual.key(e.getKey()) + ".x", String.valueOf(v[0]));
            p.setProperty("hud." + PirojokVisual.key(e.getKey()) + ".y", String.valueOf(v[1]));
        }
    }

    public static void loadAll(Properties p) {
        for (Map.Entry<String, int[]> e : POS.entrySet()) {
            try {
                String x = p.getProperty("hud." + PirojokVisual.key(e.getKey()) + ".x");
                String y = p.getProperty("hud." + PirojokVisual.key(e.getKey()) + ".y");
                if (x != null && y != null) {
                    e.getValue()[0] = Integer.parseInt(x);
                    e.getValue()[1] = Integer.parseInt(y);
                }
            } catch (Exception ignored) {}
        }
    }
}
