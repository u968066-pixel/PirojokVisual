package com.example.pirojokvisual;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

public class Visuals implements ClientModInitializer {

    public static boolean fpsEnabled = true;

    @Override
    public void onInitializeClient() {

        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            MinecraftClient client = MinecraftClient.getInstance();

            if (!fpsEnabled) {
                return;
            }

            int fps = client.getCurrentFps();

            drawContext.drawText(
                    client.textRenderer,
                    Text.literal("FPS: " + fps),
                    5,
                    5,
                    0xFFFFFF,
                    true
            );
        });
    }
}
