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
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class PirojokVisual implements ClientModInitializer {
	private static final String CAT = "Pirozhok Visuals";

	static boolean hudOn = true, showXyz = true, showFps = true, showPing = true, showTotems = true;
	static boolean night = false, autoSprint = false;

	private KeyBinding menuKey, hudKey, nightKey, zoomKey;
	private boolean zooming = false, wasNight = false;
	private int savedFov = 70;

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
		while (menuKey.wasPressed()) mc.setScreen(new MenuScreen());
		while (hudKey.wasPressed()) hudOn = !hudOn;
		while (nightKey.wasPressed()) night = !night;

		if (night) {
			StatusEffectInstance e = mc.player.getStatusEffect(StatusEffects.NIGHT_VISION);
			if (e == null || e.getDuration() < 220)
				mc.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 400, 0, false, false, false));
		} else if (wasNight) {
			mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
		}
		wasNight = night;

		if (autoSprint && mc.options.forwardKey.isPressed() && !mc.player.isSneaking()
				&& !mc.player.horizontalCollision && mc.player.getHungerManager().getFoodLevel() > 6) {
			mc.player.setSprinting(true);
		}

		var fov = mc.options.getFov();
		boolean held = zoomKey.isPressed();
		if (held && !zooming) { savedFov = fov.getValue(); zooming = true; }
		if (held) fov.setValue(30);
		else if (zooming) { fov.setValue(savedFov); zooming = false; }
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
		for (String s : left) { ctx.drawTextWithShadow(mc.textRenderer, s, 4, y, 0xFFFFFF); y += 10; }

		List<String> right = new ArrayList<>();
		if (night) right.add("NightVision");
		if (autoSprint) right.add("AutoSprint");
		if (zooming) right.add("Zoom");
		int w = mc.getWindow().getScaledWidth();
		y = 4;
		for (String s : right) {
			ctx.drawTextWithShadow(mc.textRenderer, s, w - mc.textRenderer.getWidth(s) - 4, y, 0x55FF55);
			y += 10;
		}
	}

	static class MenuScreen extends Screen {
		private int idx = 0;

		MenuScreen() { super(Text.literal("Pirozhok Visuals")); }

		@Override
		protected void init() {
			idx = 0;
			toggle("HUD", () -> hudOn, v -> hudOn = v);
			toggle("Coords", () -> showXyz, v -> showXyz = v);
			toggle("FPS", () -> showFps, v -> showFps = v);
			toggle("Ping", () -> showPing, v -> showPing = v);
			toggle("Totems", () -> showTotems, v -> showTotems = v);
			toggle("Night vision", () -> night, v -> night = v);
			toggle("Auto sprint", () -> autoSprint, v -> autoSprint = v);
			int w = 110;
			int y = height / 2 - 40 + 4 * 24;
			addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> close())
					.dimensions(width / 2 - w / 2, y, w, 20).build());
		}

		private void toggle(String name, BooleanSupplier get, Consumer<Boolean> set) {
			int w = 110;
			int x = (idx % 2 == 0) ? width / 2 - w - 5 : width / 2 + 5;
			int y = height / 2 - 40 + (idx / 2) * 24;
			addDrawableChild(ButtonWidget.builder(label(name, get.getAsBoolean()), b -> {
				set.accept(!get.getAsBoolean());
				b.setMessage(label(name, get.getAsBoolean()));
			}).dimensions(x, y, w, 20).build());
			idx++;
		}

		private Text label(String n, boolean on) {
			return Text.literal(n + ": " + (on ? "ON" : "OFF"));
		}

		@Override
		public boolean shouldPause() { return false; }

		@Override
		public void render(DrawContext ctx, int mx, int my, float delta) {
			super.render(ctx, mx, my, delta);
			ctx.drawCenteredTextWithShadow(textRenderer, "Pirozhok Visuals", width / 2, height / 2 - 60, 0xFFFFFF);
		}
	}
			}
