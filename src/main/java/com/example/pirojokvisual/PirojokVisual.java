package com.example.pirojokvisual;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
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

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class PirojokVisual implements ClientModInitializer {
	private static final String CAT = "Pirozhok Visuals";

	static boolean hudOn = true, showXyz = true, showFps = true, showPing = true, showTotems = true;
	static boolean night = false, bright = false, autoSprint = false, sounds = true;
	static int zoomFov = 30;
	static int colorIdx = 0;

	static final String[] COLOR_NAMES = {"White", "Green", "Cyan", "Pink", "Orange", "Rainbow"};
	static final int[] COLORS = {0xFFFFFF, 0x55FF55, 0x55FFFF, 0xFF77CC, 0xFFAA00, 0};

	private KeyBinding menuKey, hudKey, nightKey, zoomKey;
	private boolean zoomActive = false, wasNight = false, wasBright = false;
	private int savedFov = 70;
	private float curFov = 70f;
	private double savedGamma = 1.0;

	static int themeColor() {
		if (colorIdx == COLORS.length - 1) {
			double t = (System.currentTimeMillis() % 4000) / 4000.0 * Math.PI * 2;
			int r = (int) (127 + 127 * Math.sin(t));
			int g = (int) (127 + 127 * Math.sin(t + 2.094));
			int b = (int) (127 + 127 * Math.sin(t + 4.188));
			return 0xFF000000 | (r << 16) | (g << 8) | b;
		}
		return 0xFF000000 | COLORS[colorIdx];
	}

	static void click(boolean on) {
		if (!sounds) return;
		MinecraftClient.getInstance().getSoundManager()
			.play(PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_PLING, on ? 1.7f : 0.7f));
	}

	static void openSound() {
		if (!sounds) return;
		MinecraftClient.getInstance().getSoundManager()
			.play(PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_CHIME, 1.2f));
	}

	@Override
	public void onInitializeClient() {
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
		while (hudKey.wasPressed()) { hudOn = !hudOn; click(hudOn); }
		while (nightKey.wasPressed()) { night = !night; click(night); }

		// Night vision
		if (night) {
			StatusEffectInstance e = mc.player.getStatusEffect(StatusEffects.NIGHT_VISION);
			if (e == null || e.getDuration() < 220)
				mc.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 400, 0, false, false, false));
		} else if (wasNight) {
			mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
		}
		wasNight = night;

		// Brightness (gamma 0..1)
		var gamma = mc.options.getGamma();
		if (bright != wasBright) {
			if (bright) { savedGamma = gamma.getValue(); gamma.setValue(1.0); }
			else gamma.setValue(savedGamma);
			wasBright = bright;
		}

		// Auto sprint
		if (autoSprint && mc.options.forwardKey.isPressed() && !mc.player.isSneaking()
				&& !mc.player.horizontalCollision && mc.player.getHungerManager().getFoodLevel() > 6) {
			mc.player.setSprinting(true);
		}

		// Smooth zoom
		var fov = mc.options.getFov();
		boolean held = zoomKey.isPressed();
		if (held && !zoomActive) { savedFov = fov.getValue(); curFov = savedFov; zoomActive = true; }
		if (zoomActive) {
			float target = held ? zoomFov : savedFov;
			curFov += (target - curFov) * 0.4f;
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
		if (!hudOn || mc.player == null || mc.options.hudHidden) return;
		int col = themeColor();

		List<String> left = new ArrayList<>();
		left.add("Pirozhok");
		if (showXyz) left.add(String.format("XYZ: %.1f %.1f %.1f", mc.player.getX(), mc.player.getY(), mc.player.getZ()));
		if (showFps) left.add("FPS: " + mc.getCurrentFps());
		if (showPing) {
			int ping = 0;
			if (mc.getNetworkHandler() != null) {
				PlayerListEntry pe = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
				if (pe != null) ping = pe.getLatency();
			}
			left.add("Ping: " + ping + " ms");
		}
		if (showTotems) left.add("Totems: " + totems(mc));
		int y = 4;
		for (String s : left) { ctx.drawTextWithShadow(mc.textRenderer, s, 4, y, col); y += 10; }

		List<String> right = new ArrayList<>();
		if (night) right.add("NightVision");
		if (bright) right.add("Brightness");
		if (autoSprint) right.add("AutoSprint");
		if (zoomActive) right.add("Zoom");
		int w = mc.getWindow().getScaledWidth();
		y = 4;
		for (String s : right) {
			ctx.drawTextWithShadow(mc.textRenderer, s, w - mc.textRenderer.getWidth(s) - 4, y, col);
			y += 10;
		}
	}

	static class MenuScreen extends Screen {
		MenuScreen() { super(Text.literal("Pirozhok Visuals")); }

		@Override
		protected void init() {
			toggle(0, "HUD", () -> hudOn, v -> hudOn = v);
			toggle(1, "Coords", () -> showXyz, v -> showXyz = v);
			toggle(2, "FPS", () -> showFps, v -> showFps = v);
			toggle(3, "Ping", () -> showPing, v -> showPing = v);
			toggle(4, "Totems", () -> showTotems, v -> showTotems = v);
			toggle(5, "Night vision", () -> night, v -> night = v);
			toggle(6, "Brightness", () -> bright, v -> bright = v);
			toggle(7, "Auto sprint", () -> autoSprint, v -> autoSprint = v);
			btn(8, Text.literal("Zoom FOV: " + zoomFov), b -> {
				zoomFov = zoomFov >= 50 ? 30 : zoomFov + 10;
				b.setMessage(Text.literal("Zoom FOV: " + zoomFov));
				click(true);
			});
			btn(9, Text.literal("Color: " + COLOR_NAMES[colorIdx]), b -> {
				colorIdx = (colorIdx + 1) % COLOR_NAMES.length;
				b.setMessage(Text.literal("Color: " + COLOR_NAMES[colorIdx]));
				click(true);
			});
			toggle(10, "Sounds", () -> sounds, v -> sounds = v);
			btn(11, Text.literal("Close"), b -> close());
		}

		private void btn(int i, Text text, ButtonWidget.PressAction action) {
			int w = 110;
			int x = (i % 2 == 0) ? width / 2 - w - 5 : width / 2 + 5;
			int y = height / 2 - 70 + (i / 2) * 24;
			addDrawableChild(ButtonWidget.builder(text, action).dimensions(x, y, w, 20).build());
		}

		private void toggle(int i, String name, BooleanSupplier get, Consumer<Boolean> set) {
			btn(i, label(name, get.getAsBoolean()), b -> {
				boolean v = !get.getAsBoolean();
				set.accept(v);
				b.setMessage(label(name, v));
				click(v);
			});
		}

		private Text label(String n, boolean on) {
			return Text.literal(n + ": " + (on ? "ON" : "OFF"));
		}

		@Override
		public boolean shouldPause() { return false; }

		@Override
		public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
			if (keyCode == GLFW.GLFW_KEY_M) { close(); return true; }
			return super.keyPressed(keyCode, scanCode, modifiers);
		}

		@Override
		public void render(DrawContext ctx, int mx, int my, float delta) {
			super.render(ctx, mx, my, delta);
			ctx.drawCenteredTextWithShadow(textRenderer, "Pirozhok Visuals", width / 2, height / 2 - 88, themeColor());
		}
	}
	}
