package com.example.pirojokvisual;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import org.lwjgl.glfw.GLFW;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public class PirojokVisual implements ClientModInitializer {
	private static final String CAT = "Pirozhok Visuals";

	static class Setting {
		final String name; final int type; boolean b; float v, min, max, step; String[] opts; int idx; float tg;
		Setting(String n, int t) { name = n; type = t; }
		static Setting bool(String n, boolean d) { Setting s = new Setting(n, 0); s.b = d; s.tg = d ? 1f : 0f; return s; }
		static Setting slider(String n, float min, float max, float step, float d) { Setting s = new Setting(n, 1); s.min = min; s.max = max; s.step = step; s.v = d; return s; }
		static Setting mode(String n, int d, String... o) { Setting s = new Setting(n, 2); s.opts = o; s.idx = d; return s; }
		String shown() {
			if (type == 0) return b ? "ON" : "OFF";
			if (type == 1) return step < 1f ? String.format("%.1f", v) : String.valueOf(Math.round(v));
			return opts[idx];
		}
		String ser() { return type == 0 ? String.valueOf(b) : (type == 1 ? String.valueOf(v) : String.valueOf(idx)); }
		void des(String t) {
			try {
				if (type == 0) { b = Boolean.parseBoolean(t); tg = b ? 1f : 0f; }
				else if (type == 1) v = Math.max(min, Math.min(max, Float.parseFloat(t)));
				else idx = Math.max(0, Math.min(opts.length - 1, Integer.parseInt(t)));
			} catch (Exception ignored) {}
		}
	}

	static class Mod {
		final String name, cat; final boolean always; boolean enabled, open; float anim, openAnim; final List<Setting> sets = new ArrayList<>();
		Mod(String n, String c, boolean def, boolean always) { name = n; cat = c; enabled = def; this.always = always; anim = def ? 1f : 0f; }
		Mod add(Setting s) { sets.add(s); return this; }
	}

	static final Setting S_ZOOM = Setting.slider("FOV", 10, 70, 5, 30), S_SMOOTH = Setting.bool("Smooth", true);
	static final Setting S_CROSS = Setting.mode("Style", 2, "Dot", "Ticks", "Both"), S_LOWHP = Setting.slider("HP", 2, 10, 1, 6);
	static final Setting S_HSOUND = Setting.mode("Sound", 3, "Pling", "Bell", "Bit", "Orb", "Arrow"), S_HPITCH = Setting.slider("Pitch", 0.5f, 2.0f, 0.1f, 1.2f);
	static final Setting S_COLOR = Setting.mode("Color", 2, "White", "Green", "Cyan", "Pink", "Orange", "Rainbow");
	static final Setting S_SOUND = Setting.bool("Sounds", true), S_ROUND = Setting.slider("Rounding", 0, 10, 1, 5), S_BLUR = Setting.bool("Blur", true);

	static final Mod WATERMARK = new Mod("Watermark", "HUD", true, false), COORDS = new Mod("Coordinates", "HUD", true, false);
	static final Mod FPS = new Mod("FPS", "HUD", true, false), PING = new Mod("Ping", "HUD", true, false), CPS = new Mod("CPS", "HUD", true, false);
	static final Mod SPEED = new Mod("Speed", "HUD", false, false), TIME = new Mod("Time", "HUD", false, false), TOTEMS = new Mod("Totems", "HUD", true, false);
	static final Mod ARMOR = new Mod("Armor", "HUD", true, false), EFFECTS = new Mod("Effects", "HUD", true, false), KEYS = new Mod("Keystrokes", "HUD", false, false);
	static final Mod ARRAYLIST = new Mod("ArrayList", "HUD", true, false), NIGHT = new Mod("Night Vision", "Visuals", false, false), BRIGHT = new Mod("Brightness", "Visuals", false, false);
	static final Mod ZOOM = new Mod("Zoom", "Visuals", true, false).add(S_ZOOM).add(S_SMOOTH), CROSS = new Mod("Crosshair", "Visuals", false, false).add(S_CROSS);
	static final Mod LOWHP = new Mod("Low HP Alert", "Visuals", false, false).add(S_LOWHP), SPRINT = new Mod("Auto Sprint", "Player", false, false);
	static final Mod HITSOUND = new Mod("Hit Sound", "Player", false, false).add(S_HSOUND).add(S_HPITCH), IFACE = new Mod("Interface", "Client", true, true).add(S_COLOR).add(S_SOUND).add(S_ROUND).add(S_BLUR);
	static final Mod LITE = new Mod("Lite Mode", "Client", false, false);
	static final Mod TARGET_HUD = new Mod("TargetHUD", "Combat", false, false);
	static final Mod AUTO_RESPAWN = new Mod("Auto Respawn", "Player", false, false);
	static final Mod NO_HURT_CAM = new Mod("NoHurtCam", "Visuals", false, false);

	static final Mod[] ALL = {WATERMARK, COORDS, FPS, PING, CPS, SPEED, TIME, TOTEMS, ARMOR, EFFECTS, KEYS, ARRAYLIST, NIGHT, BRIGHT, ZOOM, CROSS, LOWHP, SPRINT, HITSOUND, IFACE, LITE, TARGET_HUD, AUTO_RESPAWN, NO_HURT_CAM};
	static final String[] CATS = {"HUD", "Visuals", "Player", "Combat", "Client"};
	static final int[] COLORS = {0xFFFFFF, 0x55FF55, 0x55FFFF, 0xFF77CC, 0xFFAA00, 0};
	static final int UNSET = Integer.MIN_VALUE; static final int[] PX = new int[5], PY = new int[5];
	static boolean hudVisible = true; static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

	static { Arrays.fill(PX, UNSET); Arrays.fill(PY, UNSET); }
	public static boolean hideVanillaCrosshair() { return CROSS.enabled; }
	static List<Mod> modsOf(String cat) { List<Mod> l = new ArrayList<>(); for (Mod m : ALL) if (m.cat.equals(cat)) l.add(m); return l; }
	static boolean isHudVisible(String name) {
		return switch (name) {
			case "Watermark" -> WATERMARK.enabled;
			case "Coordinates" -> COORDS.enabled;
			case "FPS" -> FPS.enabled;
			case "Ping" -> PING.enabled;
			case "CPS" -> CPS.enabled;
			case "Speed" -> SPEED.enabled;
			case "Time" -> TIME.enabled;
			case "Effects" -> EFFECTS.enabled;
			case "Keystrokes" -> KEYS.enabled;
			case "ArrayList" -> ARRAYLIST.enabled;
			case "Totems" -> TOTEMS.enabled;
			case "Armor" -> ARMOR.enabled;
			default -> false;
		};
	}
	public static int themeColor() {
		if (S_COLOR.idx == COLORS.length - 1) {
			double t = (System.currentTimeMillis() % 4000) / 4000.0 * Math.PI * 2;
			return 0xFF000000 | ((int) (127 + 127 * Math.sin(t)) << 16) | ((int) (127 + 127 * Math.sin(t + 2.094)) << 8) | (int) (127 + 127 * Math.sin(t + 4.188));
		}
		return 0xFF000000 | COLORS[S_COLOR.idx];
	}
	static String key(String s) { return s.replace(' ', '_'); }
	static Path cfgPath() { return FabricLoader.getInstance().getConfigDir().resolve("pirojokvisual.properties"); }
	public static void save() {
		try {
			Properties p = new Properties(); p.setProperty("hudVisible", String.valueOf(hudVisible));
			for (Mod m : ALL) {
				p.setProperty("m." + key(m.name), String.valueOf(m.enabled));
				for (Setting s : m.sets) p.setProperty("s." + key(m.name) + "." + key(s.name), s.ser());
			}
			for (int i = 0; i < CATS.length; i++) { p.setProperty("px" + i, String.valueOf(PX[i])); p.setProperty("py" + i, String.valueOf(PY[i])); }
			HudDrag.saveAll(p);
			try (OutputStream o = Files.newOutputStream(cfgPath())) { p.store(o, "Pirozhok Visuals"); }
		} catch (Exception ignored) {}
	}

	static void load() {
		Path f = cfgPath(); if (!Files.exists(f)) return;
		try (InputStream i = Files.newInputStream(f)) {
			Properties p = new Properties(); p.load(i);
			String h = p.getProperty("hudVisible"); if (h != null) hudVisible = Boolean.parseBoolean(h);
			for (Mod m : ALL) {
				String e = p.getProperty("m." + key(m.name)); if (e != null && !m.always) { m.enabled = Boolean.parseBoolean(e); m.anim = m.enabled ? 1f : 0f; }
				for (Setting s : m.sets) { String v = p.getProperty("s." + key(m.name) + "." + key(s.name)); if (v != null) s.des(v); }
			}
			for (int c = 0; c < CATS.length; c++) {
				try { String x = p.getProperty("px" + c), y = p.getProperty("py" + c); if (x != null && y != null) { PX[c] = Integer.parseInt(x); PY[c] = Integer.parseInt(y); } } catch (Exception ignored) {}
			}
			HudDrag.loadAll(p);
		} catch (Exception ignored) {}
	}

	static void click(boolean on) { save(); if (S_SOUND.b) MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_PLING, on ? 1.7f : 0.7f)); }
	static void openSound() { if (S_SOUND.b) MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_CHIME, 1.2f)); }

	static void playHit() {
		var sm = MinecraftClient.getInstance().getSoundManager(); float p = S_HPITCH.v;
		switch (S_HSOUND.idx) {
			case 0 -> sm.play(PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_PLING, p));
			case 1 -> sm.play(PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_BELL, p));
			case 2 -> sm.play(PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_BIT, p));
			case 3 -> sm.play(PositionedSoundInstance.master(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, p));
			default -> sm.play(PositionedSoundInstance.master(SoundEvents.ENTITY_ARROW_HIT_PLAYER, p));
		}
	}

	private KeyBinding menuKey, hudKey, nightKey, zoomKey, hudEditorKey;
	private boolean zoomActive = false, wasNight = false, wasBright = false, lastAttack = false;
	private int savedFov = 70; private float curFov = 70f; private double savedGamma = 0.5;
	private final List<Long> clicks = new ArrayList<>();
	private int tickCount = 0; private double prevX, prevZ; private float speed = 0f;
	private String[] infoLines = new String[0], effLines = new String[0], arrNames = new String[0];
	private int[] infoW = new int[0], effW = new int[0], arrW = new int[0];
	private int totemCount = 0; private ItemStack totemStack;

	@Override
	public void onInitializeClient() {
		menuKey = reg("Open menu", GLFW.GLFW_KEY_M); hudKey = reg("HUD on/off", GLFW.GLFW_KEY_H);
		nightKey = reg("Night vision", GLFW.GLFW_KEY_N); zoomKey = reg("Zoom (hold)", GLFW.GLFW_KEY_C);
		hudEditorKey = reg("HUD Editor", GLFW.GLFW_KEY_RIGHT_SHIFT);
		HudDrag.register("Watermark", 4, 4, 90, 14);
		HudDrag.register("Coordinates", 4, 22, 110, 11);
		HudDrag.register("FPS", 4, 34, 70, 11);
		HudDrag.register("Ping", 4, 46, 90, 11);
		HudDrag.register("CPS", 4, 58, 70, 11);
		HudDrag.register("Speed", 4, 70, 100, 11);
		HudDrag.register("Time", 4, 82, 60, 11);
		HudDrag.register("Effects", 4, 96, 130, 11);
		HudDrag.register("ArrayList", 300, 4, 100, 11);
		HudDrag.register("Totems", 0, 0, 20, 20);
		HudDrag.register("Armor", 0, 0, 90, 20);
		load();
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		HudRenderCallback.EVENT.register((ctx, tc) -> render(ctx));
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> { if (world.isClient && HITSOUND.enabled) playHit(); return ActionResult.PASS; });
	}

	private KeyBinding reg(String name, int key) { return KeyBindingHelper.registerKeyBinding(new KeyBinding(name, InputUtil.Type.KEYSYM, key, CAT)); }
	
private void tick(MinecraftClient mc) {
	if (mc.player == null) return;
	while (menuKey.wasPressed()) { if (mc.currentScreen == null) { openSound(); mc.setScreen(new MenuScreen()); } }
	while (hudEditorKey.wasPressed()) { if (mc.currentScreen == null) { openSound(); mc.setScreen(new HudEditorScreen()); } }
	while (hudKey.wasPressed()) { hudVisible = !hudVisible; click(hudVisible); }
	while (nightKey.wasPressed()) { NIGHT.enabled = !NIGHT.enabled; click(NIGHT.enabled); }

	long now = System.currentTimeMillis(); boolean atk = mc.options.attackKey.isPressed();
	if (atk && !lastAttack) clicks.add(now); lastAttack = atk; clicks.removeIf(t -> now - t > 1000);
	double dx = mc.player.getX() - prevX, dz = mc.player.getZ() - prevZ; prevX = mc.player.getX(); prevZ = mc.player.getZ();
	speed = speed * 0.8f + (float) (Math.sqrt(dx * dx + dz * dz) * 20) * 0.2f;
	if (++tickCount % (LITE.enabled ? 10 : 2) == 0) refresh(mc);

	if (NIGHT.enabled) {
		StatusEffectInstance e = mc.player.getStatusEffect(StatusEffects.NIGHT_VISION);
		if (e == null || e.getDuration() < 220) mc.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 400, 0, false, false, false));
	} else if (wasNight) { mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION); }
	wasNight = NIGHT.enabled;

	var gamma = mc.options.getGamma();
	if (BRIGHT.enabled != wasBright) {
		if (BRIGHT.enabled) { double g = gamma.getValue(); savedGamma = g >= 1.0 ? 0.5 : g; gamma.setValue(1.0); }
		else { gamma.setValue(savedGamma); } wasBright = BRIGHT.enabled;
	}
	if (SPRINT.enabled && mc.options.forwardKey.isPressed() && !mc.player.isSneaking() && !mc.player.horizontalCollision && mc.player.getHungerManager().getFoodLevel() > 6) { mc.player.setSprinting(true); }

	if (AUTO_RESPAWN.enabled && mc.player.isDead()) { mc.player.requestRespawn(); }
	if (NO_HURT_CAM.enabled && mc.player.hurtTime > 0) { mc.player.hurtTime = 0; mc.player.timeUntilRegen = 0; }

	var fov = mc.options.getFov(); boolean held = ZOOM.enabled && mc.currentScreen == null && zoomKey.isPressed();
	if (held && !zoomActive) { savedFov = fov.getValue(); curFov = savedFov; zoomActive = true; }
	if (zoomActive) {
		float target = held ? S_ZOOM.v : savedFov; curFov = S_SMOOTH.b ? curFov + (target - curFov) * 0.4f : target; fov.setValue(Math.round(curFov));
		if (!held && Math.abs(curFov - savedFov) < 1f) { fov.setValue(savedFov); zoomActive = false; }
	}
}

private int countTotems(MinecraftClient mc) {
	int n = 0; for (int i = 0; i < 36; i++) { ItemStack s = mc.player.getInventory().getStack(i); if (s.isOf(Items.TOTEM_OF_UNDYING)) n += s.getCount(); }
	ItemStack off = mc.player.getOffHandStack(); if (off.isOf(Items.TOTEM_OF_UNDYING)) n += off.getCount(); return n;
}

private void refresh(MinecraftClient mc) {
	TextRenderer tr = mc.textRenderer; var p = mc.player; List<String> info = new ArrayList<>();
	if (COORDS.enabled) info.add(String.format("XYZ: %.1f %.1f %.1f", p.getX(), p.getY(), p.getZ()));
	if (FPS.enabled) info.add("FPS: " + mc.getCurrentFps());
	if (PING.enabled) {
		int ping = 0; if (mc.getNetworkHandler() != null) { PlayerListEntry pe = mc.getNetworkHandler().getPlayerListEntry(p.getUuid()); if (pe != null) ping = pe.getLatency(); }
		info.add("Ping: " + ping + " ms");
	}
	if (CPS.enabled) info.add("CPS: " + clicks.size()); if (SPEED.enabled) info.add(String.format("Speed: %.1f b/s", speed));
	if (TIME.enabled) info.add(LocalTime.now().format(TIME_FMT));
	infoLines = info.toArray(new String[0]); infoW = new int[infoLines.length]; for (int i = 0; i < infoLines.length; i++) infoW[i] = tr.getWidth(infoLines[i]);

	List<String> eff = new ArrayList<>();
	if (EFFECTS.enabled) {
		for (StatusEffectInstance e : p.getStatusEffects()) {
			if (NIGHT.enabled && e.getEffectType().equals(StatusEffects.NIGHT_VISION)) continue;
			String n = e.getEffectType().value().getName().getString(); if (e.getAmplifier() > 0) n += " " + (e.getAmplifier() + 1);
			int sec = e.getDuration() / 20; eff.add(n + "  " + (e.isInfinite() ? "inf" : String.format("%d:%02d", sec / 60, sec % 60)));
		}
	}
	effLines = eff.toArray(new String[0]); effW = new int[effLines.length]; for (int i = 0; i < effLines.length; i++) effW[i] = tr.getWidth(effLines[i]);

	List<String> names = new ArrayList<>();
	if (ARRAYLIST.enabled) {
		for (Mod m : ALL) { if (m.always || !m.enabled || m.cat.equals("HUD")) continue; if (m == ZOOM && !zoomActive) continue; names.add(m.name); }
		if (!LITE.enabled) names.sort((a, b) -> tr.getWidth(b) - tr.getWidth(a));
	}
	arrNames = names.toArray(new String[0]); arrW = new int[arrNames.length]; for (int i = 0; i < arrNames.length; i++) arrW[i] = tr.getWidth(arrNames[i]);
	totemCount = countTotems(mc);
}
	
	private void render(DrawContext ctx) { MinecraftClient mc = MinecraftClient.getInstance(); if (mc.player != null && !mc.options.hudHidden) draw(ctx, mc); }
	private void key(DrawContext ctx, TextRenderer tr, int x, int y, int w, String label, boolean down, int col) { ctx.fill(x, y, x + w, y + 14, down ? ((col & 0x00FFFFFF) | 0xAA000000) : 0x80101018); ctx.drawTextWithShadow(tr, label, x + (w - tr.getWidth(label)) / 2, y + 3, down ? 0xFF000000 : 0xFFFFFFFF); }

	private void draw(DrawContext ctx, MinecraftClient mc) {
		TextRenderer tr = mc.textRenderer; int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight(), col = themeColor();
		if (CROSS.enabled && mc.currentScreen == null) {
			int cx = sw / 2, cy = sh / 2, st = S_CROSS.idx;
			if (st == 0 || st == 2) ctx.fill(cx - 1, cy - 1, cx + 1, cy + 1, col);
			if (st == 1 || st == 2) { ctx.fill(cx - 1, cy - 12, cx + 1, cy - 8, col); ctx.fill(cx - 1, cy + 8, cx + 1, cy + 12, col); ctx.fill(cx - 12, cy - 1, cx - 8, cy + 1, col); ctx.fill(cx + 8, cy - 1, cx + 12, cy + 1, col); }
		}
		if (LOWHP.enabled && mc.player.isAlive() && mc.player.getHealth() <= S_LOWHP.v) {
			int c = ((int) (60 + 50 * Math.sin(System.currentTimeMillis() / 180.0)) << 24) | 0xFF2020; int t = 10;
			ctx.fill(0, 0, sw, t, c); ctx.fill(0, sh - t, sw, sh, c); ctx.fill(0, t, t, sh - t, c); ctx.fill(sw - t, t, sw, sh - t, c);
		}
		if (!hudVisible) return;
		int[] wp = HudDrag.pos("Watermark");
		if (WATERMARK.enabled) {
			String wm = "Pirozhok  |  " + mc.getCurrentFps() + " FPS  |  0 ms";
			int wmw = tr.getWidth(wm);
			ctx.fill(wp[0], wp[1], wp[0] + wmw + 16, wp[1] + 14, 0xAA101018);
			ctx.fill(wp[0], wp[1], wp[0] + 2, wp[1] + 14, col);
			ctx.drawTextWithShadow(tr, wm, wp[0] + 6, wp[1] + 3, col);
		}
		int[] cp = HudDrag.pos("Coordinates");
		if (COORDS.enabled) { String s = String.format("XYZ: %.1f %.1f %.1f", mc.player.getX(), mc.player.getY(), mc.player.getZ()); int w = tr.getWidth(s); ctx.fill(cp[0], cp[1], cp[0] + w + 8, cp[1] + 11, 0x80101018); ctx.fill(cp[0], cp[1], cp[0] + 1, cp[1] + 11, col); ctx.drawTextWithShadow(tr, s, cp[0] + 4, cp[1] + 2, 0xFFFFFFFF); }
		int[] fp = HudDrag.pos("FPS");
		if (FPS.enabled) { String s = "FPS: " + mc.getCurrentFps(); int w = tr.getWidth(s); ctx.fill(fp[0], fp[1], fp[0] + w + 8, fp[1] + 11, 0x80101018); ctx.fill(fp[0], fp[1], fp[0] + 1, fp[1] + 11, col); ctx.drawTextWithShadow(tr, s, fp[0] + 4, fp[1] + 2, 0xFFFFFFFF); }
		int[] pp = HudDrag.pos("Ping");
		if (PING.enabled) { int ping = 0; if (mc.getNetworkHandler() != null) { PlayerListEntry pe = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid()); if (pe != null) ping = pe.getLatency(); } String s = "Ping: " + ping + " ms"; int w = tr.getWidth(s); ctx.fill(pp[0], pp[1], pp[0] + w + 8, pp[1] + 11, 0x80101018); ctx.fill(pp[0], pp[1], pp[0] + 1, pp[1] + 11, col); ctx.drawTextWithShadow(tr, s, pp[0] + 4, pp[1] + 2, 0xFFFFFFFF); }
		int[] cpsp = HudDrag.pos("CPS");
		if (CPS.enabled) { String s = "CPS: " + clicks.size(); int w = tr.getWidth(s); ctx.fill(cpsp[0], cpsp[1], cpsp[0] + w + 8, cpsp[1] + 11, 0x80101018); ctx.fill(cpsp[0], cpsp[1], cpsp[0] + 1, cpsp[1] + 11, col); ctx.drawTextWithShadow(tr, s, cpsp[0] + 4, cpsp[1] + 2, 0xFFFFFFFF); }
		int[] sp = HudDrag.pos("Speed");
		if (SPEED.enabled) { String s = String.format("Speed: %.1f b/s", speed); int w = tr.getWidth(s); ctx.fill(sp[0], sp[1], sp[0] + w + 8, sp[1] + 11, 0x80101018); ctx.fill(sp[0], sp[1], sp[0] + 1, sp[1] + 11, col); ctx.drawTextWithShadow(tr, s, sp[0] + 4, sp[1] + 2, 0xFFFFFFFF); }
		int[] tp = HudDrag.pos("Time");
		if (TIME.enabled) { String s = LocalTime.now().format(TIME_FMT); int w = tr.getWidth(s); ctx.fill(tp[0], tp[1], tp[0] + w + 8, tp[1] + 11, 0x80101018); ctx.fill(tp[0], tp[1], tp[0] + 1, tp[1] + 11, col); ctx.drawTextWithShadow(tr, s, tp[0] + 4, tp[1] + 2, 0xFFFFFFFF); }
		int[] ep = HudDrag.pos("Effects");
		if (effLines.length > 0) { for (int i = 0; i < effLines.length; i++) { ctx.fill(ep[0], ep[1] + i * 12, ep[0] + effW[i] + 8, ep[1] + i * 12 + 11, 0x80101018); ctx.fill(ep[0], ep[1] + i * 12, ep[0] + 1, ep[1] + i * 12 + 11, 0xFFAA55FF); ctx.drawTextWithShadow(tr, effLines[i], ep[0] + 4, ep[1] + i * 12 + 2, 0xFFFFFFFF); } }
		int[] ap = HudDrag.pos("ArrayList");
		for (int i = 0; i < arrNames.length; i++) { int x1 = ap[0] + ap[2] - arrW[i] - 8; ctx.fill(x1, ap[1] + i * 12, ap[0] + ap[2], ap[1] + i * 12 + 11, 0x80101018); ctx.fill(ap[0] + ap[2] - 2, ap[1] + i * 12, ap[0] + ap[2], ap[1] + i * 12 + 11, col); ctx.drawTextWithShadow(tr, arrNames[i], x1 + 3, ap[1] + i * 12 + 2, col); }
		int[] kp = HudDrag.pos("Keystrokes");
		if (KEYS.enabled) { int kx = kp[0], ky = kp[1]; key(ctx, tr, kx + 16, ky, 14, "W", mc.options.forwardKey.isPressed(), col); key(ctx, tr, kx, ky + 16, 14, "A", mc.options.leftKey.isPressed(), col); key(ctx, tr, kx + 16, ky + 16, 14, "S", mc.options.backKey.isPressed(), col); key(ctx, tr, kx + 32, ky + 16, 14, "D", mc.options.rightKey.isPressed(), col); key(ctx, tr, kx, ky + 32, 46, "Space", mc.options.jumpKey.isPressed(), col); }
		int[] totp = HudDrag.pos("Totems");
		if (TOTEMS.enabled) { if (totemStack == null) totemStack = new ItemStack(Items.TOTEM_OF_UNDYING); int ix = totp[0], iy = totp[1]; ctx.drawItem(totemStack, ix, iy); String n = String.valueOf(totemCount); ctx.drawTextWithShadow(tr, n, ix - tr.getWidth(n) - 2, iy + 4, totemCount > 0 ? 0xFFFFFFFF : 0xFFFF5555); }
		int[] arp = HudDrag.pos("Armor");
		if (ARMOR.enabled) { EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}; for (int i = 0; i < 4; i++) { ItemStack s = mc.player.getEquippedStack(slots[i]); if (s.isEmpty()) continue; int x = arp[0] + i * 20; ctx.drawItem(s, x, arp[1]); if (s.isDamageable()) { ctx.drawStackOverlay(tr, s, x, arp[1]); int pct = 100 - s.getDamage() * 100 / Math.max(1, s.getMaxDamage()); String t = pct + "%"; ctx.drawTextWithShadow(tr, t, x + 8 - tr.getWidth(t) / 2, arp[1] + 17, pct > 50 ? 0xFF55FF55 : (pct > 20 ? 0xFFFFAA00 : 0xFFFF5555)); } } }
		if (TARGET_HUD.enabled && mc.targetedEntity instanceof LivingEntity le) {
			String name = le.getName().getString();
			float hp = le.getHealth(), max = le.getMaxHealth();
			double dist = mc.player.distanceTo(le);
			int x = sw / 2 - 60, y = sh / 2 + 20;
			ctx.fill(x - 2, y - 2, x + 122, y + 40, 0xAA101018);
			ctx.fill(x - 2, y - 2, x + 122, y, col);
			ctx.drawTextWithShadow(tr, name, x + 2, y + 4, 0xFFFFFFFF);
			ctx.fill(x, y + 18, x + 118, y + 24, 0xFF200000);
			ctx.fill(x, y + 18, x + (int) (118 * (hp / max)), y + 24, 0xFFFF2222);
			ctx.drawTextWithShadow(tr, String.format("%.1f/%.1f", hp, max), x + 2, y + 26, 0xFFFFFFFF);
			ctx.drawTextWithShadow(tr, String.format("%.1fm", dist), x + 90, y + 26, 0xFFAAAAAA);
		}
	}

	static class MenuScreen extends Screen {
		static final int SW = 90;
		int selectedCat = 0;
		String query = "";
		boolean searchFocus = false;
		Setting dragSlider = null;
		int sliderX;

		MenuScreen() { super(Text.literal("Pirozhok Visuals")); }
		@Override protected void init() { HudDrag.editorMode = false; }

		static int lerp(int c1, int c2, float t) { return ((int) (((c1 >>> 24) & 255) * (1 - t) + ((c2 >>> 24) & 255) * t) << 24) | ((int) (((c1 >> 16) & 255) * (1 - t) + ((c2 >> 16) & 255) * t) << 16) | ((int) (((c1 >> 8) & 255) * (1 - t) + ((c2 >> 8) & 255) * t) << 8) | (int) ((c1 & 255) * (1 - t) + (c2 & 255) * t); }
		static int alpha(int color, int a) { return (color & 0x00FFFFFF) | (a << 24); }
		static void rrect(DrawContext ctx, int x1, int y1, int x2, int y2, int r, int color) {
			r = Math.min(r, Math.min((x2 - x1) / 2, (y2 - y1) / 2)); if (r <= 0) { ctx.fill(x1, y1, x2, y2, color); return; }
			ctx.fill(x1, y1 + r, x2, y2 - r, color);
			for (int i = 0; i < r; i++) { double dy = r - i - 0.5; int inset = (int) Math.round(r - Math.sqrt(r * r - dy * dy)); ctx.fill(x1 + inset, y1 + i, x2 - inset, y1 + i + 1, color); ctx.fill(x1 + inset, y2 - i - 1, x2 - inset, y2 - i, color); }
		}
		@Override public boolean shouldPause() { return false; }
		@Override public void removed() { save(); }
		@Override public boolean keyPressed(int key, int scan, int mod) {
			if (searchFocus) {
				if (key == GLFW.GLFW_KEY_BACKSPACE) { if (!query.isEmpty()) query = query.substring(0, query.length() - 1); return true; }
				if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_ESCAPE) { searchFocus = false; return true; }
				return true;
			}
			if (key == GLFW.GLFW_KEY_M) { close(); return true; }
			return super.keyPressed(key, scan, mod);
		}
		@Override public boolean charTyped(char chr, int mod) { if (searchFocus && chr >= 32 && chr != 127 && query.length() < 20) { query += chr; return true; } return super.charTyped(chr, mod); }
		private int catX() { return 10; }
		private int catY(int i) { return 60 + i * 30; }
		private int modX() { return 110; }
		private int modY() { return 60; }
		private int modW() { return 200; }
		@Override
		public boolean mouseClicked(double mx, double my, int button) {
			for (int i = 0; i < CATS.length; i++) {
				int cx = catX(), cy = catY(i);
				if (mx >= cx && mx <= cx + SW && my >= cy && my <= cy + 24) { selectedCat = i; click(true); return true; }
			}
			int sx = modX(), sy = 20;
			if (mx >= sx && mx <= sx + modW() && my >= sy && my <= sy + 16) { searchFocus = true; return true; }
			searchFocus = false;
			int my2 = modY();
			for (Mod m : modsOf(CATS[selectedCat])) {
				if (!query.isEmpty() && !m.name.toLowerCase().contains(query.toLowerCase())) continue;
				if (mx >= modX() && mx <= modX() + modW() && my >= my2 && my <= my2 + 20) {
					if (m.open && m.openAnim >= 1f) {
						int sy2 = my2 + 20;
						for (Setting s : m.sets) { if (my >= sy2 && my <= sy2 + 16) { handleSetting(s, modX(), mx); return true; } sy2 += 16; }
					}
					if (button == 1 || (!m.sets.isEmpty() && mx >= modX() + modW() - 20)) { m.open = !m.open; click(m.open); }
					else if (button == 0 && !m.always) { m.enabled = !m.enabled; click(m.enabled); }
					return true;
				}
				my2 += 20 + (int) (m.openAnim * m.sets.size() * 16);
			}
			return super.mouseClicked(mx, my, button);
		}
		private void handleSetting(Setting s, int px, double mx) {
			if (s.type == 0) { s.b = !s.b; click(s.b); }
			else if (s.type == 2) { s.idx = (s.idx + 1) % s.opts.length; click(true); }
			else { dragSlider = s; sliderX = px; setSlider(mx); }
		}
		private void setSlider(double mx) {
			float t = Math.max(0f, Math.min(1f, (float) ((mx - (sliderX + 8)) / (modW() - 16))));
			float val = Math.round((dragSlider.min + t * (dragSlider.max - dragSlider.min)) / dragSlider.step) * dragSlider.step;
			dragSlider.v = Math.max(dragSlider.min, Math.min(dragSlider.max, val));
		}
		@Override public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) { if (dragSlider != null) { setSlider(mx); return true; } return super.mouseDragged(mx, my, btn, dx, dy); }
		@Override public boolean mouseReleased(double mx, double my, int btn) { if (dragSlider != null) { dragSlider = null; save(); return true; } return super.mouseReleased(mx, my, btn); }
		@Override
		public void render(DrawContext ctx, int mx, int my, float delta) {
			int accent = themeColor();
			int rad = Math.round(S_ROUND.v);
			if (S_BLUR.b && !LITE.enabled) renderBackground(ctx, mx, my, delta);
			else ctx.fill(0, 0, width, height, 0xCC000000);
			ctx.drawTextWithShadow(textRenderer, "PIROZHOK", 10, 10, accent);
			ctx.fill(0, 0, 100, height, 0xAA0A1420);
			for (int i = 0; i < CATS.length; i++) {
				int cx = catX(), cy = catY(i);
				boolean sel = i == selectedCat;
				rrect(ctx, cx, cy, cx + SW, cy + 24, rad, sel ? alpha(accent, 0x99) : 0x80152030);
				ctx.drawTextWithShadow(textRenderer, CATS[i], cx + 10, cy + 8, sel ? 0xFFFFFFFF : 0xFFAAAAAA);
			}
			int sx = modX(), sy = 20;
			rrect(ctx, sx - 1, sy - 1, sx + modW() + 1, sy + 17, rad, alpha(accent, 0x88));
			rrect(ctx, sx, sy, sx + modW(), sy + 16, rad, 0xFF0F1A2C);
			String shownQ = query.isEmpty() && !searchFocus ? "Search..." : query + (((System.currentTimeMillis() / 450) % 2 == 0) && searchFocus ? "_" : "");
			ctx.drawTextWithShadow(textRenderer, shownQ, sx + 6, sy + 4, query.isEmpty() && !searchFocus ? 0xFF6F7D92 : 0xFFFFFFFF);
			int my2 = modY();
			for (Mod m : modsOf(CATS[selectedCat])) {
				if (!query.isEmpty() && !m.name.toLowerCase().contains(query.toLowerCase())) continue;
				m.anim += ((m.enabled ? 1f : 0f) - m.anim) * 0.3f;
				m.openAnim += ((m.open ? 1f : 0f) - m.openAnim) * 0.3f;
				boolean hover = mx >= modX() && mx <= modX() + modW() && my >= my2 && my <= my2 + 20;
				rrect(ctx, modX(), my2, modX() + modW(), my2 + 20, rad, lerp(hover ? 0xFF203753 : 0xFF182A42, alpha(accent, 0x55), m.anim));
				if (m.anim > 0f) ctx.fill(modX(), my2, modX() + 3, my2 + 20, alpha(accent, (int) (255 * m.anim)));
				ctx.drawTextWithShadow(textRenderer, m.name, modX() + 10, my2 + 6, m.always ? 0xFFFFFFFF : lerp(0xFFAEBBCF, 0xFFFFFFFF, m.anim));
				if (!m.sets.isEmpty()) {
					ctx.drawTextWithShadow(textRenderer, m.open ? "-" : "+", modX() + modW() - 15, my2 + 6, accent);
					if (m.enabled && m.sets.size() > 0 && !m.open) ctx.drawTextWithShadow(textRenderer, m.sets.get(0).shown(), modX() + modW() - 40, my2 + 6, accent);
				}
				my2 += 20;
				if (m.open && m.openAnim > 0f) {
					int sh = (int) (m.openAnim * m.sets.size() * 16);
					ctx.enableScissor(modX(), my2, modX() + modW(), my2 + sh);
					int sy2 = my2;
					for (Setting s : m.sets) {
						ctx.fill(modX(), sy2, modX() + modW(), sy2 + 16, 0xFF12203A);
						ctx.fill(modX() + 5, sy2, modX() + 6, sy2 + 16, alpha(accent, 0x66));
						ctx.drawTextWithShadow(textRenderer, s.name, modX() + 10, sy2 + 4, 0xFFAEBBCF);
						if (s.type == 0) {
							int pw = 24, ph = 11, px1 = modX() + modW() - 8 - pw, py1 = sy2 + 2;
							s.tg += ((s.b ? 1f : 0f) - s.tg) * 0.35f;
							rrect(ctx, px1, py1, px1 + pw, py1 + ph, ph / 2, lerp(0xFF2A3A52, accent, s.tg));
							int kx = px1 + 1 + (int) ((pw - ph) * s.tg);
							rrect(ctx, kx, py1 + 1, kx + ph - 2, py1 + ph - 1, (ph - 2) / 2, 0xFFFFFFFF);
						} else {
							String val = s.shown();
							ctx.drawTextWithShadow(textRenderer, val, modX() + modW() - 8 - textRenderer.getWidth(val), sy2 + 4, accent);
							if (s.type == 1) {
								int bx1 = modX() + 10, bx2 = modX() + modW() - 8, by = sy2 + 13;
								ctx.fill(bx1, by, bx2, by + 2, 0xFF2A3A52);
								ctx.fill(bx1, by, bx1 + (int) ((bx2 - bx1) * ((s.v - s.min) / (s.max - s.min))), by + 2, accent);
							}
						}
						sy2 += 16;
					}
					ctx.disableScissor();
					my2 += sh;
				}
			}
			ctx.drawTextWithShadow(textRenderer, "M = close  |  LMB = toggle  |  RMB = settings", 10, height - 12, 0xFF8FA3BD);
		}
	}
					}
