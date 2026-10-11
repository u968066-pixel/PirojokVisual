package com.example.pirojokvisual.mixin;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public class ChinaHatMixin {

    private static boolean hatEnabled() {
        try {
            Class<?> c = Class.forName("com.example.pirojokvisual.PirojokVisual");
            java.lang.reflect.Field f = c.getDeclaredField("CHINA_HAT");
            f.setAccessible(true);
            Object mod = f.get(null);
            java.lang.reflect.Field e = mod.getClass().getDeclaredField("enabled");
            e.setAccessible(true);
            return (boolean) e.get(mod);
        } catch (Exception ex) {
            return false;
        }
    }

    @Inject(method = "render*", at = @At("TAIL"))
    private void pirojok$renderHat(Object state, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        if (!hatEnabled()) return;
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f mat = entry.getPositionMatrix();
        VertexConsumer vc = vertexConsumers.getBuffer(net.minecraft.client.render.RenderLayer.getLines());
        float y = -1.5f;
        float r = 0.5f;
        addLine(vc, mat, -r, y, -r, r, y, -r);
        addLine(vc, mat, r, y, -r, r, y, r);
        addLine(vc, mat, r, y, r, -r, y, r);
        addLine(vc, mat, -r, y, r, -r, y, -r);
        addLine(vc, mat, -r, y, -r, 0, y - 0.7f, 0);
        addLine(vc, mat, r, y, -r, 0, y - 0.7f, 0);
        addLine(vc, mat, r, y, r, 0, y - 0.7f, 0);
        addLine(vc, mat, -r, y, r, 0, y - 0.7f, 0);
    }

    private void addLine(VertexConsumer vc, Matrix4f mat, float x1, float y1, float z1, float x2, float y2, float z2) {
        vc.vertex(mat, x1, y1, z1).color(0xFFAA00FF);
        vc.vertex(mat, x2, y2, z2).color(0xFFAA00FF);
    }
}
