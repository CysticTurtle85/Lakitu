package net.cystic.lakitu.flight.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.cystic.lakitu.flight.FlyingMount;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.EntityType;
import org.jspecify.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.base.RenderPassInfo;

/**
 * Draws a {@link FlyingMount} with GeckoLib: its body faces its smoothly turning yaw, it floats up and down with its
 * rider and leans into turns and speed, around a point {@code pivotY} blocks up (about the middle of the model). Extend
 * it for a mount's renderer; call {@code super} when overriding {@link #addRenderData} or {@link #adjustRenderPose}.
 *
 * <p>Framework module file (flying-mount): improve it in the minecraft-multiloader-mods skill
 * (assets/modules/flying-mount) and sync it into every mod that has it.
 */
public class FlyingMountRenderer<T extends FlyingMount & GeoAnimatable, R extends LivingEntityRenderState & GeoRenderState>
        extends GeoEntityRenderer<T, R> {
    /** {bob blocks, lean degrees, tilt degrees} for this frame. */
    private static final DataTicket<float[]> FLIGHT_POSE = DataTicket.create("lakitu_flight_pose", float[].class);
    private final float pivotY;

    public FlyingMountRenderer(EntityRendererProvider.Context context, EntityType<? extends T> type, float pivotY) {
        super(context, type);
        this.pivotY = pivotY;
    }

    @Override
    public void addRenderData(T mount, @Nullable Void relatedObject, R renderState, float partialTick) {
        renderState.addGeckolibData(DataTickets.ENTITY_BODY_YAW, mount.visualYaw(partialTick));
        // The seat moves once a tick and the rider is drawn between ticks, so the float trails a tick to stay level.
        renderState.addGeckolibData(FLIGHT_POSE, new float[] {mount.bob(renderState.ageInTicks - 1.0F), mount.lean(partialTick), mount.tilt(partialTick)});
    }

    @Override
    public void adjustRenderPose(RenderPassInfo<R> renderPassInfo) {
        super.adjustRenderPose(renderPassInfo);
        float[] pose = renderPassInfo.getGeckolibData(FLIGHT_POSE);
        if (pose == null)
            return;
        PoseStack poseStack = renderPassInfo.poseStack();
        poseStack.translate(0.0F, pose[0] + this.pivotY, 0.0F);
        // Model space: +X is the mount's right, -Z its front. Leaning right lowers +X; dipping lowers the front.
        poseStack.rotateDegrees(Axis.ZP, -pose[1]);
        poseStack.rotateDegrees(Axis.XP, -pose[2]);
        poseStack.translate(0.0F, -this.pivotY, 0.0F);
    }
}
