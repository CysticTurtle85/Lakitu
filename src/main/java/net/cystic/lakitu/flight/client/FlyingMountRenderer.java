package net.cystic.lakitu.flight.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.cystic.lakitu.flight.FlightPose;
import net.cystic.lakitu.flight.FlyingMount;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.EntityType;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
//#if MC >= 1.21.5
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.jspecify.annotations.Nullable;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.renderer.base.GeoRenderState;
//#if MC >= 1.21.11
import software.bernie.geckolib.renderer.base.RenderPassInfo;
//#elif MC >= 1.21.9
import net.minecraft.client.renderer.state.CameraRenderState;
import software.bernie.geckolib.cache.model.BakedGeoModel;
//#endif
//#endif
//#if MC < 1.21.9
import net.minecraft.core.registries.BuiltInRegistries;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
//#endif
//#if MC >= 1.21.5 && MC < 1.21.9
import net.minecraft.client.Minecraft;
//#endif

/**
 * Draws a {@link FlyingMount} with GeckoLib: its body faces its smoothly turning yaw, it floats up and down with its
 * rider and leans into turns and speed around its {@code leanPivot} ({@link FlightLean}). Extend it for a mount's
 * renderer; call {@code super} when overriding the hooks below.
 *
 * <p>One hook per GeckoLib generation: 5.4+ (1.21.11+) `adjustRenderPose(RenderPassInfo)`; 5.3 (1.21.10)
 * `adjustRenderPose(state, poseStack, model, camera)`; 5.1-5.2 (1.21.5-1.21.8) `applyRotations(state, poseStack,
 * scale)`; 4.x (1.20.1-1.21.4, no render states) `applyRotations(entity, ...)` with the yaw passed in, reading the
 * entity directly. 4.x renderers have no render-state type parameter.
 *
 * <p>Framework module file (flying-mount): improve it in the minecraft-multiloader-mods skill
 * (assets/modules/flying-mount) and sync it into every mod that has it.
 */
//#if MC >= 1.21.5
public class FlyingMountRenderer<T extends FlyingMount & GeoAnimatable, R extends LivingEntityRenderState & GeoRenderState>
        extends GeoEntityRenderer<T, R> {
    /** {bob blocks, lean pivot, lean degrees, tilt degrees} for this frame. */
    private static final DataTicket<float[]> FLIGHT_POSE = DataTicket.create("lakitu_flight_pose", float[].class);

    public FlyingMountRenderer(EntityRendererProvider.Context context, EntityType<? extends T> type) {
        //#if MC >= 1.21.9
        super(context, type);
        //#else
        super(context, new DefaultedEntityGeoModel<>(BuiltInRegistries.ENTITY_TYPE.getKey(type)));
        //#endif
    }

    @Override
    //#if MC >= 1.21.9
    public void addRenderData(T mount, @Nullable Void relatedObject, R renderState, float partialTick) {
    //#else
    public void addRenderData(T mount, @Nullable Void relatedObject, R renderState) {
        float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
    //#endif
        FlightPose pose = mount.pose();
        renderState.addGeckolibData(DataTickets.ENTITY_BODY_YAW, pose.yaw(partialTick));
        // The seat moves once a tick and the rider is drawn between ticks, so the float trails a tick to stay level.
        renderState.addGeckolibData(FLIGHT_POSE, new float[] {mount.bob(renderState.ageInTicks - 1.0F), mount.leanPivot(),
                pose.lean(partialTick), pose.tilt(partialTick)});
    }

    //#if MC >= 1.21.11
    @Override
    public void adjustRenderPose(RenderPassInfo<R> renderPassInfo) {
        super.adjustRenderPose(renderPassInfo);
        lean(renderPassInfo.poseStack(), renderPassInfo.getGeckolibData(FLIGHT_POSE));
    }
    //#elif MC >= 1.21.9
    @Override
    public void adjustRenderPose(R renderState, PoseStack poseStack, BakedGeoModel model, CameraRenderState camera) {
        super.adjustRenderPose(renderState, poseStack, model, camera);
        lean(poseStack, renderState.getGeckolibData(FLIGHT_POSE));
    }
    //#else
    @Override
    protected void applyRotations(R renderState, PoseStack poseStack, float nativeScale) {
        super.applyRotations(renderState, poseStack, nativeScale);
        lean(poseStack, renderState.getGeckolibData(FLIGHT_POSE));
    }
    //#endif

    private static void lean(PoseStack poseStack, float[] pose) {
        if (pose != null)
            FlightLean.inModel(poseStack, pose[0], pose[1], pose[2], pose[3]);
    }
}
//#else
public class FlyingMountRenderer<T extends FlyingMount & GeoAnimatable> extends GeoEntityRenderer<T> {
    public FlyingMountRenderer(EntityRendererProvider.Context context, EntityType<? extends T> type) {
        // Models at geo/entity/<id>.geo.json, textures at textures/entity/<id>.png.
        super(context, new DefaultedEntityGeoModel<>(BuiltInRegistries.ENTITY_TYPE.getKey(type)));
    }

    @Override
    //#if MC >= 1.21
    protected void applyRotations(T mount, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick, float nativeScale) {
        FlightPose pose = mount.pose();
        super.applyRotations(mount, poseStack, ageInTicks, pose.yaw(partialTick), partialTick, nativeScale);
    //#else
    protected void applyRotations(T mount, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick) {
        FlightPose pose = mount.pose();
        super.applyRotations(mount, poseStack, ageInTicks, pose.yaw(partialTick), partialTick);
    //#endif
        FlightLean.inModel(poseStack, mount.bob(ageInTicks - 1.0F), mount.leanPivot(), pose.lean(partialTick), pose.tilt(partialTick));
    }
}
//#endif
