package com.example.pirojokvisual;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public class PirojokVisual implements ClientModInitializer {
	private static final String CAT = "Pirozhok Visuals";

	// ---------- model ----------
	static class Setting {
		final String name;
		final int type; // 0 bool, 1 slider, 2 mode
		boolean b;
		float v, min, max, step;
		String[] opts;
		int idx;

		Setting(String n, int t) { name = n; type = t; }

		static Setting bool(String n, boolean d) { Setting s = new Setting(n, 0); s.b = d; return s; }

		static Setting slider(String n, float min, float max, float step, float d) {
			Setting s = new Setting(n, 1);
			s.min = min; s.max = max; s.step = step; s.v = d;
			return s;
		}

		static Setting mode(String n, int d, String... o) {
			Setting s = new Setting(n, 2);
			s.opts = o; s.idx = d;
			return s;
		}

		String shown() {
			if (type == 0) return b ? "ON" : "OFF";
			if (type == 1) return String.valueOf(Math.round(v));
			return opts[idx];
		}

		String ser() {
			if (type == 0) return String.valueOf(b);
			if (type == 1) return String.valueOf(v);
			return String.valueOf(idx);
		}

		void des(String t) {
			try {
				if (type == 0) b = Boolean.parseBoolean(t);
				else if (type == 1) v = Math.max(min, Math.min(max, Float.parseFloat(t)));
				else idx = Math.max(0, Math.min(opts.length - 1, Integer.parseInt(t)));
			} catch (Exception ignored) {
			}
		}
	}

	static class Mod {
		final String name, cat;
		final boolean always;
		boolean enabled, open;
		float anim, openAnim;
		final List<Setting> sets = new ArrayList<>();

		Mod(String n, String c, boolean def, boolean always) {
			name = n; cat = c; enabled = def; this.always = always;
			anim = def ? 1f : 0f;
		}

		Mod add(Setting s) { sets.add(s); return this; }
	}

	static final Setting S_ZOOM = Setting.slider("FOV", 30, 70, 5, 30);
	static final Setting S_SMOOTH = Setting.bool("Smooth", true);
	static final Setting S_CROSS = Setting.mode("Style", 2, "Dot", "Ticks", "Both");
	static final Setting S_COLOR = Setting.mode("Color", 0, "White", "Green", "Cyan", "Pink", "Orange", "Rainbow");
	static final Setting S_SOUND = Setting.bool("Sounds", true);

	static final Mod WATERMARK = new Mod("Watermark", "HUD", true, false);
	static final Mod COORDS = new Mod("Coordinates", "HUD", true, false);
	static final Mod FPS = new Mod("FPS", "HUD", true, false);
	static final Mod PING = new Mod("Ping", "HUD", true, false);
	static final Mod CPS = new Mod("CPS", "HUD", true, false);
	static final Mod TOTEMS = new Mod("Totems", "HUD", true, false);
	static final Mod ARRAYLIST = new Mod("ArrayList", "HUD", true, false);
	static final Mod NIGHT = new Mod("Night Vision", "Visuals", false, false);
	static final Mod BRIGHT = new Mod("Brightness", "Visuals", false, false);
	static final Mod ZOOM = new Mod("Zoom", "Visuals", true, false).add(S_ZOOM).add(S_SMOOTH);
	static final Mod CROSS = new Mod("Crosshair", "Visuals", false, false).add(S_CROSS);
	static final Mod SPRINT = new Mod("Auto Sprint", "Player", false, false);
	static final Mod IFACE = new Mod("Interface", "Client", true, true).add(S_COLOR).add(S_SOUND);

	static final Mod[] ALL = {WATERMARK, COORDS, FPS, PING, CPS, TOTEMS, ARRAYLIST,
			NIGHT, BRIGHT, ZOOM, CROSS, SPRINT, IFACE};
	static final String[] CATS = {"HUD", "Visuals", "Player", "Client"};
	static final int[] COLORS = {0xFFFFFF, 0x55FF55, 0x55FFFF, 0xFF77CC, 0xFFAA00, 0};

	static final int UNSET = Integer.MIN_VALUE;
	static final int[] PX = new int[4];
	static final int[] PY = new int[4];
	static boolean hudVisible = true;

	static {
		Arrays.fill(PX, UNSET);
		Arrays.fill(PY, UNSET);
	}

	static List<Mod> modsOf(String cat) {
		List<Mod> l = new ArrayList<>();
		for (Mod m : ALL) if (m.cat.equals(cat)) l.add(m);
		return l;
	}

	// ---------- config ----------
	static String key(String s) { return s.replace(' ', '_'); }

	static Path cfgPath() {
		return FabricLoader.getInstance().getConfigDir().resolve("pirojokvisual.properties");
	}

	static void save() {
		try {
			Properties p = new Properties();
			p.setProperty("hudVisible", String.valueOf(hudVisible));
			for (Mod m : ALL) {
				p.setProperty("m." + key(m.name), String.valueOf(m.enabled));
				for (Setting s : m.sets) p.setProperty("s." + key(m.name) + "." + key(s.name), s.ser());
			}
			for (int i = 0; i < CATS.length; i++) {
				p.setProperty("px" + i, String.valueOf(PX[i]));
				p.setProperty("py" + i, String.valueOf(PY[i]));
			}
			try (OutputStream o = Files.newOutputStream(cfgPath())) {
				p.store(o, "Pirozhok Visuals");
			}
		} catch (Exception ignored) {
		}
	}

	static void load() {
		Path f = cfgPath();
		if (!Files.exists(f)) return;
		try (InputStream i = Files.newInputStream(f)) {
			Properties p = new Properties();
			p.load(i);
			String h = p.getProperty("hudVisible");
			if (h != null) hudVisible = Boolean.parseBoolean(h);
			for (Mod m : ALL) {
				String e = p.getProperty("m." + key(m.name));
				if (e != null && !m.always) { m.enabled = Boolean.parseBoolean(e); m.anim = m.enabled ? 1f : 0f; }
				for (Setting s : m.sets) {
					String v = p.getProperty("s." + key(m.name) + "." + key(s.name));
					if (v != null) s.des(v);
				}
			}
			for (int c = 0; c < CATS.length; c++) {
				try {
					String x = p.getProperty("px" + c), y = p.getProperty("py" + c);
					if (x != null && y != null) { PX[c] = Integer.parseInt(x); PY[c] = Integer.parseInt(y); }
				} catch (Exception ignored) {
				}
			}
		} catch (Exception ignored) {
		}
	}

	// ---------- helpers ----------
	static int themeColor() {
		if (S_COLOR.idx == COLORS.length - 1) {
			double t = (System.currentTimeMillis() % 4000) / 4000.0 * Math.PI * 2;
			int r = (int) (127 + 127 * Math.sin(t));
			int g = (int) (127 + 127 * Math.sin(t + 2.094));
			int b = (int) (127 + 127 * Math.sin(t + 4.188));
			return 0xFF000000 | (r << 16) | (g << 8) | b;
		}
		return 0xFF000000 | COLORS[S_COLOR.idx];
	}

	static void click(boolean on) {
		save();
		if (!S_SOUND.b) return;
		MinecraftClient.getInstance().getSoundManager()
			.play(PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_PLING, on ? 1.7f : 0.7f));
	}

	static void openSound() {
		if (!S_SOUND.b) return;
		MinecraftClient.getInstance().getSoundManager()
			.play(PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_CHIME, 1.2f));
	}

	// ---------- mod logic ----------
	private KeyBinding menuKey, hudKey, nightKey, zoomKey;
	private boolean zoomActive = false, wasNight = false, wasBright = false, lastAttack = false;
	private int savedFov = 70;
	private float curFov = 70f;
	private double savedGamma = 0.5;
	private final List<Long> clicks = new ArrayList<>();

	@Override
	public void onInitializeClient() {
		load();
		menuKey = reg("Open menu", GLFW.GLFW_KEY_M);
		hudKey = reg("HUD on/off", GLFW.GLFW_KEY_H);
		nightKey = reg("Night vision", GLFW.GLFW_KEY_N);
		zoomKey = reg("Zoom (hold)", GLFW.GLFW_KEY_C);
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		HudRenderCallback.EVENT.register((ctx, tc) -> render(ctx));
	}

	private KeyBinding reg(String name, int key) {
		return KeyBindingHelper.registerKeyBinding(new KeyBinding(name, InputUtil.Type.KEYSYM, key, CAT));
	}

	private void tick(MinecraftClient mc) {
		if (mc.player == null) return;

		while (menuKey.wasPressed()) {
			if (mc.currentScreen == null) {
				openSound();
				mc.setScreen(new MenuScreen());
			}
		}
		while (hudKey.wasPressed()) { hudVisible = !hudVisible; click(hudVisible); }
		while (nightKey.wasPressed()) { NIGHT.enabled = !NIGHT.enabled; click(NIGHT.enabled); }

		long now = System.currentTimeMillis();
		boolean atk = mc.options.attackKey.isPressed();
		if (atk && !lastAttack) clicks.add(now);
		lastAttack = atk;
		clicks.removeIf(t -> now - t > 1000);

		if (NIGHT.enabled) {
			StatusEffectInstance e = mc.player.getStatusEffect(StatusEffects.NIGHT_VISION);
			if (e == null || e.getDuration() < 220)
				mc.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 400, 0, false, false, false));
		} else if (wasNight) {
			mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
		}
		wasNight = NIGHT.enabled;

		var gamma = mc.options.getGamma();
		if (BRIGHT.enabled != wasBright) {
			if (BRIGHT.enabled) {
				double g = gamma.getValue();
				savedGamma = g >= 1.0 ? 0.5 : g;
				gamma.setValue(1.0);
			} else {
				gamma.setValue(savedGamma);
			}
			wasBright = BRIGHT.enabled;
		}

		if (SPRINT.enabled && mc.options.forwardKey.isPressed() && !mc.player.isSneaking()
				&& !mc.player.horizontalCollision && mc.player.getHungerManager().getFoodLevel() > 6) {
			mc.player.setSprinting(true);
		}

		var fov = mc.options.getFov();
		boolean held = ZOOM.enabled && mc.currentScreen == null && zoomKey.isPressed();
		if (held && !zoomActive) { savedFov = fov.getValue(); curFov = savedFov; zoomActive = true; }
		if (zoomActive) {
			float target = held ? S_ZOOM.v : savedFov;
			curFov = S_SMOOTH.b ? curFov + (target - curFov) * 0.4f : target;
			fov.setValue(Math.round(curFov));
			if (!held && Math.abs(curFov - savedFov) < 1f) { fov.setValue(savedFov); zoomActive = false; }
		}
	}

	private int totems(MinecraftClient mc) {
		int n = 0;
		for (int i = 0; i < 36; i++) {
			ItemStack s = mc.player.getInventory().getStack(i);
			if (s.isOf(Items.TOTEM_OF_UNDYING)) n += s.getCount();
		}
		ItemStack off = mc.player.getOffHandStack();
		if (off.isOf(Items.TOTEM_OF_UNDYING)) n += off.getCount();
		return n;
	}

	private void render(DrawContext ctx) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || mc.options.hudHidden) return;
		final TextRenderer tr = mc.textRenderer;
		int col = themeColor();

		if (CROSS.enabled && mc.currentScreen == null) {
			int cx = mc.getWindow().getScaledWidth() / 2;
			int cy = mc.getWindow().getScaledHeight() / 2;
			int st = S_CROSS.idx;
			if (st == 0 || st == 2) ctx.fill(cx - 1, cy - 1, cx + 1, cy + 1, col);
			if (st == 1 || st == 2) {
				ctx.fill(cx - 1, cy - 12, cx + 1, cy - 8, col);
				ctx.fill(cx - 1, cy + 8, cx + 1, cy + 12, col);
				ctx.fill(cx - 12, cy - 1, cx - 8, cy + 1, col);
				ctx.fill(cx + 8, cy - 1, cx + 12, cy + 1, col);
			}
		}

		if (!hudVisible) return;

		int y = 4;
		if (WATERMARK.enabled) {
			String title = "Pirozhok";
			int w = tr.getWidth(title) + 10;
			ctx.fill(4, y, 4 + w, y + 14, 0xAA101018);
			ctx.fill(4, y, 6, y + 14, col);
			ctx.drawTextWithShadow(tr, title, 10, y + 3, col);
			y += 18;
		}

		List<String> info = new ArrayList<>();
		if (COORDS.enabled) info.add(String.format("XYZ: %.1f %.1f %.1f", mc.player.getX(), mc.player.getY(), mc.player.getZ()));
		if (FPS.enabled) info.add("FPS: " + mc.getCurrentFps());
		if (PING.enabled) {
			int ping = 0;
			if (mc.getNetworkHandler() != null) {
				PlayerListEntry pe = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
				if (pe != null) ping = pe.getLatency();
			}
			info.add("Ping: " + ping + " ms");
		}
		if (CPS.enabled) info.add("CPS: " + clicks.size());
		if (TOTEMS.enabled) info.add("Totems: " + totems(mc));
		for (String s : info) {
			int w = tr.getWidth(s) + 8;
			ctx.fill(4, y, 4 + w, y + 11, 0x80101018);
			ctx.fill(4, y, 5, y + 11, col);
			ctx.drawTextWithShadow(tr, s, 8, y + 2, 0xFFFFFFFF);
			y += 12;
		}

		if (ARRAYLIST.enabled) {
			List<String> names = new ArrayList<>();
			for (Mod m : ALL) {
				if (m.always || !m.enabled || m.cat.equals("HUD")) continue;
				if (m == ZOOM && !zoomActive) continue;
				names.add(m.name);
			}
			names.sort((a, b) -> tr.getWidth(b) - tr.getWidth(a));
			int sw = mc.getWindow().getScaledWidth();
			int ry = 4;
			for (String s : names) {
				int tw = tr.getWidth(s);
				int x1 = sw - tw - 8;
				ctx.fill(x1, ry, sw, ry + 11, 0x80101018);
				ctx.fill(sw - 2, ry, sw, ry + 11, col);
				ctx.drawTextWithShadow(tr, s, x1 + 3, ry + 2, col);
				ry += 12;
			}
		}
	}

	// ---------- ClickGUI ----------
	static class MenuScreen extends Screen {
		static final int PW = 100, HH = 18, MH = 16, SH = 14, GAP = 6;
		int dragPanel = -1, dragDX, dragDY;
		Setting dragSlider = null;
		int sliderX;

		MenuScreen() { super(Text.literal("Pirozhok Visuals")); }

		@Override
		protected void init() {
			int total = CATS.length * PW + (CATS.length - 1) * GAP;
			int sx = Math.max(4, (width - total) / 2);
			for (int i = 0; i < CATS.length; i++) {
				if (PX[i] == UNSET || PY[i] == UNSET) { PX[i] = sx + i * (PW + GAP); PY[i] = 24; }
				PX[i] = Math.max(0, Math.min(Math.max(0, width - PW), PX[i]));
				PY[i] = Math.max(0, Math.min(Math.max(0, height - HH), PY[i]));
			}
		}

		static int lerp(int c1, int c2, float t) {
			int r = (int) (((c1 >> 16) & 255) * (1 - t) + ((c2 >> 16) & 255) * t);
			int g = (int) (((c1 >> 8) & 255) * (1 - t) + ((c2 >> 8) & 255) * t);
			int bl = (int) ((c1 & 255) * (1 - t) + (c2 & 255) * t);
			return 0xFF000000 | (r << 16) | (g << 8) | bl;
		}

		static void rrect(DrawContext ctx, int x1, int y1, int x2, int y2, int color) {
			ctx.fill(x1 + 1, y1, x2 - 1, y2, color);
			ctx.fill(x1, y1 + 1, x2, y2 - 1, color);
		}

		@Override
		public boolean shouldPause() { return false; }

		@Override
		public void removed() { save(); }

		@Override
		public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
			if (keyCode == GLFW.GLFW_KEY_M) { close(); return true; }
			return super.keyPressed(keyCode, scanCode, modifiers);
		}

		private void setSlider(double mx) {
			float t = (float) ((mx - (sliderX + 3)) / (PW - 6));
			t = Math.max(0f, Math.min(1f, t));
			float val = dragSlider.min + t * (dragSlider.max - dragSlider.min);
			val = Math.round(val / dragSlider.step) * dragSlider.step;
			dragSlider.v = Math.max(dragSlider.min, Math.min(dragSlider.max, val));
		}

		private void handleSetting(Setting s, int px, double mx) {
			if (s.type == 0) { s.b = !s.b; click(s.b); }
			else if (s.type == 2) { s.idx = (s.idx + 1) % s.opts.length; click(true); }
			else { dragSlider = s; sliderX = px; setSlider(mx); }
		}

		@Override
		public boolean mouseClicked(double mx, double my, int button) {
			for (int c = 0; c < CATS.length; c++) {
				int x = PX[c], y = PY[c];
				if (mx < x || mx > x + PW) continue;
				if (my >= y && my <= y + HH) {
					if (button == 0) { dragPanel = c; dragDX = (int) mx - x; dragDY = (int) my - y; return true; }
					continue;
				}
				int cy = y + HH;
				for (Mod m : modsOf(CATS[c])) {
					if (my >= cy && my < cy + MH) {
						boolean arrow = !m.sets.isEmpty() && mx >= x + PW - 16;
						if (button == 1 || arrow || (m.always && !m.sets.isEmpty())) {
							if (!m.sets.isEmpty()) { m.open = !m.open; click(m.open); }
						} else if (button == 0 && !m.always) {
							m.enabled = !m.enabled;
							click(m.enabled);
						}
						return true;
					}
					cy += MH;
					if (m.open && m.openAnim >= 1f) {
						for (Setting s : m.sets) {
							if (my >= cy && my < cy + SH) {
								if (button == 0) handleSetting(s, x, mx);
								return true;
							}
							cy += SH;
						}
					} else {
						cy += (int) (m.openAnim * m.sets.size() * SH);
					}
				}
			}
			return super.mouseClicked(mx, my, button);
		}

		@Override
		public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
			if (dragPanel >= 0) {
				PX[dragPanel] = Math.max(0, Math.min(Math.max(0, width - PW), (int) mx - dragDX));
				PY[dragPanel] = Math.max(0, Math.min(Math.max(0, height - HH), (int) my - dragDY));
				return true;
			}
			if (dragSlider != null) { setSlider(mx); return true; }
			return super.mouseDragged(mx, my, button, dx, dy);
		}

		@Override
		public boolean mouseReleased(double mx, double my, int button) {
			boolean was = dragPanel >= 0 || dragSlider != null;
			dragPanel = -1;
			dragSlider = null;
			if (was) save();
			return super.mouseReleased(mx, my, button);
		}

		private int panelHeight(List<Mod> mods) {
			int h = HH;
			for (Mod m : mods) h += MH + (int) (m.openAnim * m.sets.size() * SH);
			return h + 3;
		}

		@Override
		public void render(DrawContext ctx, int mx, int my, float delta) {
			int accent = themeColor();
			int onColor = lerp(0xFF181822, accent, 0.45f);
			ctx.fill(0, 0, width, height, 0x66000000);

			for (int c = 0; c < CATS.length; c++) {
				int x = PX[c], y = PY[c];
				List<Mod> mods = modsOf(CATS[c]);
				for (Mod m : mods) {
					float ta = m.enabled ? 1f : 0f;
					m.anim += (ta - m.anim) * 0.3f;
					if (Math.abs(ta - m.anim) < 0.02f) m.anim = ta;
					float to = m.open ? 1f : 0f;
					m.openAnim += (to - m.openAnim) * 0.3f;
					if (Math.abs(to - m.openAnim) < 0.02f) m.openAnim = to;
				}
				int h = panelHeight(mods);

				rrect(ctx, x - 1, y - 1, x + PW + 1, y + h + 1, 0xFF2A2A38);
				rrect(ctx, x, y, x + PW, y + h, 0xF0121219);
				rrect(ctx, x, y, x + PW, y + HH, 0xFF1C1C28);
				ctx.fill(x + 3, y + HH - 2, x + PW - 3, y + HH, accent);
				int tw = textRenderer.getWidth(CATS[c]);
				ctx.drawTextWithShadow(textRenderer, CATS[c], x + (PW - tw) / 2, y + 5, 0xFFFFFFFF);

				int cy = y + HH;
				for (Mod m : mods) {
					boolean hover = mx >= x && mx <= x + PW && my >= cy && my < cy + MH;
					int bg = lerp(hover ? 0xFF222230 : 0xFF181822, onColor, m.anim);
					ctx.fill(x + 2, cy, x + PW - 2, cy + MH, bg);
					if (m.enabled) ctx.fill(x + 2, cy, x + 4, cy + MH, accent);
					int tcol = m.always ? 0xFFFFFFFF : lerp(0xFFAAAAAA, 0xFFFFFFFF, m.anim);
					ctx.drawTextWithShadow(textRenderer, m.name, x + 8, cy + 4, tcol);
					if (!m.sets.isEmpty())
						ctx.drawTextWithShadow(textRenderer, m.open ? "v" : ">", x + PW - 12, cy + 4, 0xFF999999);
					cy += MH;

					int sh = (int) (m.openAnim * m.sets.size() * SH);
					if (sh > 0) {
						ctx.enableScissor(x, cy, x + PW, cy + sh);
						int sy = cy;
						for (Setting s : m.sets) {
							ctx.fill(x + 2, sy, x + PW - 2, sy + SH, 0xFF0F0F16);
							if (s.type == 1) {
								float t = (s.v - s.min) / (s.max - s.min);
								ctx.fill(x + 3, sy + 1, x + 3 + (int) ((PW - 6) * t), sy + SH - 1,
										(accent & 0x00FFFFFF) | 0x88000000);
							}
							if (s.type == 0 && s.b) ctx.fill(x + 3, sy + 1, x + 5, sy + SH - 1, accent);
							ctx.drawTextWithShadow(textRenderer, s.name, x + 8, sy + 3, 0xFFCCCCCC);
							String val = s.shown();
							int vw = textRenderer.getWidth(val);
							int vc = s.type == 0 ? (s.b ? accent : 0xFF777777) : accent;
							ctx.drawTextWithShadow(textRenderer, val, x + PW - 6 - vw, sy + 3, vc);
							sy += SH;
						}
						ctx.disableScissor();
					}
					cy += sh;
				}
			}

			String hint = "Tap name = on/off   |   > = settings   |   drag header = move   |   M = close";
			int hw = textRenderer.getWidth(hint);
			ctx.drawTextWithShadow(textRenderer, hint, (width - hw) / 2, height - 12, 0xFF888899);
		}
	}
	}
