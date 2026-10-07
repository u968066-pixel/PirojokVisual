package com.example.pirojokvisual;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class PirojokVisual implements ClientModInitializer {
	private static final String CAT = "Pirozhok Visuals";
	private KeyBinding hudKey, nightKey, zoomKey;
	private boolean hudOn = true, night = false, zooming = false;
	private int savedFov = 70;

	@Override
	public void onInitializeClient() {
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
		while (hudKey.wasPressed()) hudOn = !hudOn;
		while (nightKey.wasPressed()) {
			night = !night;
			if (!night) mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
		}
		if (night) {
			StatusEffectInstance e = mc.player.getStatusEffect(StatusEffects.NIGHT_VISION);
			if (e == null || e.getDuration() < 220)
				mc.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 400, 0, false, false, false));
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

		int ping = 0;
		if (mc.getNetworkHandler() != null) {
			PlayerListEntry pe = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
			if (pe != null) ping = pe.getLatency();
		}
		List<String> left = new ArrayList<>();
		left.add("Pirozhok");
		left.add(String.format("XYZ: %.1f %.1f %.1f", mc.player.getX(), mc.player.getY(), mc.player.getZ()));
		left.add("FPS: " + mc.getCurrentFps());
		left.add("Ping: " + ping + " ms");
		left.add("Totems: " + totems(mc));
		int y = 4;
		for (String s : left) { ctx.drawTextWithShadow(mc.textRenderer, s, 4, y, 0xFFFFFF); y += 10; }

		List<String> right = new ArrayList<>();
		if (night) right.add("NightVision");
		if (zooming) right.add("Zoom");
		int w = mc.getWindow().getScaledWidth();
		y = 4;
		for (String s : right) {
			ctx.drawTextWithShadow(mc.textRenderer, s, w - mc.textRenderer.getWidth(s) - 4, y, 0x55FF55);
			y += 10;
		}
	}
}
