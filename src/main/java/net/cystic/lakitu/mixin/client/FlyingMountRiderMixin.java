package net.cystic.lakitu.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.cystic.lakitu.flight.FlyingMount;
import net.cystic.lakitu.flight.client.FlightLean;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//#if MC >= 1.21.2
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
//#endif
//#if MC >= 1.21.9
import net.minecraft.client.renderer.SubmitNodeCollector;
//#if MC >= 26.1
import net.minecraft.client.renderer.state.level.CameraRenderState;
//#else
import net.minecraft.client.renderer.state.CameraRenderState;
//#endif
//#else
import net.minecraft.client.renderer.MultiBufferSource;
//#endif

/**
 * A {@link FlyingMount}'s rider leans and dips with it, around the same point, so mount and rider move as one. Works
 * for any living rider (players included: their renderer is a LivingEntityRenderer). From 1.21.2 entities are drawn
 * from render states: the pose is worked out when the state is extracted (FlightLean keeps it by state) and applied
 * right after the renderer's first pushPose; before 1.21.2 both happen in render(entity, ...). List it under "client"
 * in the mod's mixin config (as "client.FlyingMountRiderMixin").
 *
 * <p>Framework module file (flying-mount): improve it in the minecraft-multiloader-mods skill
 * (assets/modules/flying-mount) and sync it into every mod that has it.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class FlyingMountRiderMixin {
    //#if MC >= 1.21.2
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL"))
    private void lakitu$extractRiderLean(LivingEntity entity, LivingEntityRenderState state, float partialTick, CallbackInfo ci) {
        FlightLean.storeRiderLean(state, FlightLean.riderLean(entity, partialTick));
    }
    //#endif

    //#if MC >= 26.1
    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", ordinal = 0, shift = At.Shift.AFTER))
    //#elif MC >= 1.21.9
    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", ordinal = 0, shift = At.Shift.AFTER))
    //#endif
    //#if MC >= 1.21.9
    private void lakitu$leanWithMount(LivingEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        FlightLean.applyRiderLean(poseStack, FlightLean.storedRiderLean(state));
    }
    //#elif MC >= 1.21.2
    @Inject(method = "render(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", ordinal = 0, shift = At.Shift.AFTER))
    private void lakitu$leanWithMount(LivingEntityRenderState state, PoseStack poseStack, MultiBufferSource buffers, int light, CallbackInfo ci) {
        FlightLean.applyRiderLean(poseStack, FlightLean.storedRiderLean(state));
    }
    //#else
    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", ordinal = 0, shift = At.Shift.AFTER))
    private void lakitu$leanWithMount(LivingEntity entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, CallbackInfo ci) {
        FlightLean.applyRiderLean(poseStack, FlightLean.riderLean(entity, partialTick));
    }
    //#endif
}
