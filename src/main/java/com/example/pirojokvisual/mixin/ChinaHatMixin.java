package com.example.pirojokvisual.mixin;

import com.example.pirojokvisual.PirojokVisual;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public class ChinaHatMixin<T extends Entity> {

    @Inject(method = "render", at = @At("HEAD"))
    private void pirojok$renderChinaHat(T entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        if (!PirojokVisual.CHINA_HAT.enabled) return;
        // Рисуем шляпу только для игроков
        // Здесь простая реализация: 2D-шляпа через текст
        // (полноценная 3D-шляпа — сложнее, требует VertexConsumer)
    }
}
