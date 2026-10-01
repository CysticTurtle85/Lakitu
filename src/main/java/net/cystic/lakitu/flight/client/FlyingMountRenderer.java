package net.cystic.lakitu.flight.client;

import net.cystic.lakitu.flight.FlightPose;
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
 * rider and leans into turns and speed around its {@code leanPivot} ({@link FlightLean}). Extend it for a mount's
 * renderer; call {@code super} when overriding {@link #addRenderData} or {@link #adjustRenderPose}.
 *
 * <p>Framework module file (flying-mount): improve it in the minecraft-multiloader-mods skill
 * (assets/modules/flying-mount) and sync it into every mod that has it.
 */
public class FlyingMountRenderer<T extends FlyingMount & GeoAnimatable, R extends LivingEntityRenderState & GeoRenderState>
        extends GeoEntityRenderer<T, R> {
    /** {bob blocks, lean pivot, lean degrees, tilt degrees} for this frame. */
    private static final DataTicket<float[]> FLIGHT_POSE = DataTicket.create("lakitu_flight_pose", float[].class);

    public FlyingMountRenderer(EntityRendererProvider.Context context, EntityType<? extends T> type) {
        super(context, type);
    }

    @Override
    public void addRenderData(T mount, @Nullable Void relatedObject, R renderState, float partialTick) {
        FlightPose pose = mount.pose();
        renderState.addGeckolibData(DataTickets.ENTITY_BODY_YAW, pose.yaw(partialTick));
        // The seat moves once a tick and the rider is drawn between ticks, so the float trails a tick to stay level.
        renderState.addGeckolibData(FLIGHT_POSE, new float[] {mount.bob(renderState.ageInTicks - 1.0F), mount.leanPivot(),
                pose.lean(partialTick), pose.tilt(partialTick)});
    }

    @Override
    public void adjustRenderPose(RenderPassInfo<R> renderPassInfo) {
        super.adjustRenderPose(renderPassInfo);
        float[] pose = renderPassInfo.getGeckolibData(FLIGHT_POSE);
        if (pose != null)
            FlightLean.inModel(renderPassInfo.poseStack(), pose[0], pose[1], pose[2], pose[3]);
    }
}
