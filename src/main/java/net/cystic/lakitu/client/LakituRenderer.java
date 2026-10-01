package net.cystic.lakitu.client;

import net.cystic.lakitu.Lakitu;
import net.cystic.lakitu.entity.LakituEntity;
import net.cystic.lakitu.flight.client.FlightLean;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.base.BoneSnapshots;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.base.RenderPassInfo;

/**
 * The Lakitu mob: geckolib/models/entity/lakitu.geo.json, the Lakitu sitting on its cloud. The spiny egg in its right
 * hand ("held_egg") is only drawn while the throw animation plays; the animation itself scales it in and out. Its
 * cloud is grey (lakitu_rain.png) while it's a rain cloud. It leans and dips around its cloud's middle like a ridden
 * cloud (FlightLean).
 */
public class LakituRenderer<R extends LivingEntityRenderState & GeoRenderState> extends GeoEntityRenderer<LakituEntity, R> {
    private static final DataTicket<Boolean> THROWING = DataTicket.create("lakitu_throwing", Boolean.class);
    /** {lean, tilt} degrees for this frame. */
    private static final DataTicket<float[]> LEAN = DataTicket.create("lakitu_lean", float[].class);
    private static final Identifier RAIN_TEXTURE = Lakitu.id("textures/entity/lakitu_rain.png");

    public LakituRenderer(EntityRendererProvider.Context context) {
        super(context, Lakitu.lakituEntity.get());
        this.shadowRadius = 0.7F;
    }

    @Override
    public void addRenderData(LakituEntity lakitu, @Nullable Void relatedObject, R renderState, float partialTick) {
        renderState.addGeckolibData(THROWING, lakitu.isThrowing());
        renderState.addGeckolibData(LakituCloudRenderer.RAIN_CLOUD, lakitu.isRainCloud());
        renderState.addGeckolibData(LEAN, new float[] {lakitu.pose().lean(partialTick), lakitu.pose().tilt(partialTick)});
    }

    @Override
    public void adjustRenderPose(RenderPassInfo<R> renderPassInfo) {
        super.adjustRenderPose(renderPassInfo);
        float[] lean = renderPassInfo.getGeckolibData(LEAN);
        if (lean != null)
            FlightLean.inModel(renderPassInfo.poseStack(), 0.0F, LakituEntity.LOOK.leanPivot(), lean[0], lean[1]);
    }

    @Override
    public Identifier getTextureLocation(R renderState) {
        return Boolean.TRUE.equals(renderState.getGeckolibData(LakituCloudRenderer.RAIN_CLOUD)) ? RAIN_TEXTURE : super.getTextureLocation(renderState);
    }

    @Override
    public void adjustModelBonesForRender(RenderPassInfo<R> renderPassInfo, BoneSnapshots snapshots) {
        boolean throwing = Boolean.TRUE.equals(renderPassInfo.getGeckolibData(THROWING));
        snapshots.get("held_egg").ifPresent(egg -> egg.skipRender(!throwing));
    }
}
