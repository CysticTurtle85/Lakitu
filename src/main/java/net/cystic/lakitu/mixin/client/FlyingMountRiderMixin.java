package net.cystic.lakitu.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.cystic.lakitu.flight.FlightPose;
import net.cystic.lakitu.flight.FlyingMount;
import net.cystic.lakitu.flight.client.FlightLean;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/**
 * A {@link FlyingMount}'s rider leans and dips with it, around the same point, so mount and rider move as one. Works
 * for any living rider (players are drawn by AvatarRenderer, a LivingEntityRenderer). The mount's pose rides along on
 * the rider's render state as a GeckoLib data ticket (GeckoLib makes every entity render state hold them). List it
 * under "client" in the mod's mixin config (as "client.FlyingMountRiderMixin").
 *
 * <p>Framework module file (flying-mount): improve it in the minecraft-multiloader-mods skill
 * (assets/modules/flying-mount) and sync it into every mod that has it.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class FlyingMountRiderMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL"))
    private void lakitu$extractRiderLean(LivingEntity entity, LivingEntityRenderState state, float partialTick, CallbackInfo ci) {
        if (!(entity.getVehicle() instanceof FlyingMount mount))
            return;
        FlightPose pose = mount.pose();
        float lean = pose.lean(partialTick);
        float tilt = pose.tilt(partialTick);
        if (lean == 0.0F && tilt == 0.0F)
            return;
        // The mount's lean point, as the mount's renderer places it (bob trailing a tick), from the rider's position.
        Vec3 pivot = mount.getPosition(partialTick).subtract(entity.getPosition(partialTick))
                .add(0.0, mount.bob(mount.tickCount + partialTick - 1.0F) + mount.leanPivot(), 0.0);
        ((GeoRenderState) state).addGeckolibData(FlightLean.RIDER,
                new float[] {(float) pivot.x, (float) pivot.y, (float) pivot.z, pose.yaw(partialTick), lean, tilt});
    }

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", ordinal = 0, shift = At.Shift.AFTER))
    private void lakitu$leanWithMount(LivingEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        float[] lean = ((GeoRenderState) state).getGeckolibData(FlightLean.RIDER);
        if (lean != null)
            FlightLean.inWorld(poseStack, new Vec3(lean[0], lean[1], lean[2]), lean[3], lean[4], lean[5]);
    }
}
