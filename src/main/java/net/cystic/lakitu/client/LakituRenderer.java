package net.cystic.lakitu.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.cystic.lakitu.Lakitu;
import net.cystic.lakitu.entity.LakituEntity;
import net.cystic.lakitu.flight.client.FlightLean;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
//#if MC >= 1.21.5
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.jspecify.annotations.Nullable;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.renderer.base.GeoRenderState;
//#endif
//#if MC >= 1.21.11
import software.bernie.geckolib.renderer.base.BoneSnapshots;
import software.bernie.geckolib.renderer.base.RenderPassInfo;
//#else
import software.bernie.geckolib.cache.model.BakedGeoModel;
//#endif
//#if MC >= 1.21.9 && MC < 1.21.11
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.CameraRenderState;
//#endif
//#if MC < 1.21.9
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
//#endif
//#if MC < 1.21.9
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
//#endif
//#if MC >= 1.21.5 && MC < 1.21.9
import net.minecraft.client.Minecraft;
//#endif

/**
 * The Lakitu mob: geckolib/models/entity/lakitu.geo.json, the Lakitu sitting on its cloud. The spiny egg in its right
 * hand ("held_egg") is only drawn while the throw animation plays; the animation itself scales it in and out. Its
 * cloud is grey (lakitu_rain.png) while it's a rain cloud. It leans and dips around its cloud's middle like a ridden
 * cloud (FlightLean). One set of hooks per GeckoLib generation (see FlyingMountRenderer).
 */
//#if MC >= 1.21.5
public class LakituRenderer<R extends LivingEntityRenderState & GeoRenderState> extends GeoEntityRenderer<LakituEntity, R> {
    private static final DataTicket<Boolean> THROWING = DataTicket.create("lakitu_throwing", Boolean.class);
    /** {lean, tilt} degrees for this frame. */
    private static final DataTicket<float[]> LEAN = DataTicket.create("lakitu_lean", float[].class);
    private static final Identifier RAIN_TEXTURE = Lakitu.id("textures/entity/lakitu_rain.png");

    public LakituRenderer(EntityRendererProvider.Context context) {
        //#if MC >= 1.21.9
        super(context, Lakitu.lakituEntity.get());
        //#else
        super(context, new DefaultedEntityGeoModel<>(Lakitu.id("lakitu")));
        //#endif
        this.shadowRadius = 0.7F;
    }

    @Override
    //#if MC >= 1.21.9
    public void addRenderData(LakituEntity lakitu, @Nullable Void relatedObject, R renderState, float partialTick) {
    //#else
    public void addRenderData(LakituEntity lakitu, @Nullable Void relatedObject, R renderState) {
        float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
    //#endif
        renderState.addGeckolibData(THROWING, lakitu.isThrowing());
        renderState.addGeckolibData(LakituCloudRenderer.RAIN_CLOUD, lakitu.isRainCloud());
        renderState.addGeckolibData(LEAN, new float[] {lakitu.pose().lean(partialTick), lakitu.pose().tilt(partialTick)});
    }

    @Override
    public Identifier getTextureLocation(R renderState) {
        return Boolean.TRUE.equals(renderState.getGeckolibData(LakituCloudRenderer.RAIN_CLOUD)) ? RAIN_TEXTURE : super.getTextureLocation(renderState);
    }

    //#if MC >= 1.21.11
    @Override
    public void adjustRenderPose(RenderPassInfo<R> renderPassInfo) {
        super.adjustRenderPose(renderPassInfo);
        lean(renderPassInfo.poseStack(), renderPassInfo.getGeckolibData(LEAN));
    }

    @Override
    public void adjustModelBonesForRender(RenderPassInfo<R> renderPassInfo, BoneSnapshots snapshots) {
        boolean throwing = Boolean.TRUE.equals(renderPassInfo.getGeckolibData(THROWING));
        snapshots.get("held_egg").ifPresent(egg -> egg.skipRender(!throwing));
    }
    //#elif MC >= 1.21.9
    @Override
    public void adjustRenderPose(R renderState, PoseStack poseStack, BakedGeoModel model, CameraRenderState camera) {
        super.adjustRenderPose(renderState, poseStack, model, camera);
        lean(poseStack, renderState.getGeckolibData(LEAN));
    }

    @Override
    public void preRender(R renderState, PoseStack poseStack, BakedGeoModel model, SubmitNodeCollector collector, CameraRenderState camera,
                          int packedLight, int packedOverlay, int renderColor) {
        super.preRender(renderState, poseStack, model, collector, camera, packedLight, packedOverlay, renderColor);
        boolean throwing = Boolean.TRUE.equals(renderState.getGeckolibData(THROWING));
        model.getBone("held_egg").ifPresent(egg -> egg.setHidden(!throwing));
    }
    //#else
    @Override
    protected void applyRotations(R renderState, PoseStack poseStack, float nativeScale) {
        super.applyRotations(renderState, poseStack, nativeScale);
        lean(poseStack, renderState.getGeckolibData(LEAN));
    }

    @Override
    public void preRender(R renderState, PoseStack poseStack, BakedGeoModel model, MultiBufferSource bufferSource, VertexConsumer buffer,
                          boolean isReRender, int packedLight, int packedOverlay, int renderColor) {
        super.preRender(renderState, poseStack, model, bufferSource, buffer, isReRender, packedLight, packedOverlay, renderColor);
        boolean throwing = Boolean.TRUE.equals(renderState.getGeckolibData(THROWING));
        model.getBone("held_egg").ifPresent(egg -> egg.setHidden(!throwing));
    }
    //#endif

    private static void lean(PoseStack poseStack, float[] lean) {
        if (lean != null)
            FlightLean.inModel(poseStack, 0.0F, LakituEntity.LOOK.leanPivot(), lean[0], lean[1]);
    }
}
//#else
public class LakituRenderer<R> extends GeoEntityRenderer<LakituEntity> {
    private static final Identifier RAIN_TEXTURE = Lakitu.id("textures/entity/lakitu_rain.png");

    public LakituRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(Lakitu.id("lakitu")));
        this.shadowRadius = 0.7F;
    }

    @Override
    public Identifier getTextureLocation(LakituEntity lakitu) {
        return lakitu.isRainCloud() ? RAIN_TEXTURE : super.getTextureLocation(lakitu);
    }

    @Override
    //#if MC >= 1.21
    protected void applyRotations(LakituEntity lakitu, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick, float nativeScale) {
        super.applyRotations(lakitu, poseStack, ageInTicks, rotationYaw, partialTick, nativeScale);
    //#else
    protected void applyRotations(LakituEntity lakitu, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick) {
        super.applyRotations(lakitu, poseStack, ageInTicks, rotationYaw, partialTick);
    //#endif
        FlightLean.inModel(poseStack, 0.0F, LakituEntity.LOOK.leanPivot(), lakitu.pose().lean(partialTick), lakitu.pose().tilt(partialTick));
    }

    @Override
    //#if MC >= 1.21
    public void preRender(PoseStack poseStack, LakituEntity lakitu, BakedGeoModel model, MultiBufferSource bufferSource, VertexConsumer buffer,
                          boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        super.preRender(poseStack, lakitu, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
    //#else
    public void preRender(PoseStack poseStack, LakituEntity lakitu, BakedGeoModel model, MultiBufferSource bufferSource, VertexConsumer buffer,
                          boolean isReRender, float partialTick, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        super.preRender(poseStack, lakitu, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, red, green, blue, alpha);
    //#endif
        boolean throwing = lakitu.isThrowing();
        model.getBone("held_egg").ifPresent(egg -> egg.setHidden(!throwing));
    }
}
//#endif
