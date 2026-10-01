package net.cystic.lakitu.client;

import net.cystic.lakitu.Lakitu;
import net.cystic.lakitu.entity.LakituCloudEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.base.RenderPassInfo;

/**
 * The rideable cloud: geckolib/models/entity/lakitu_cloud.geo.json, the Lakitu model without the Lakitu. Grey
 * (lakitu_cloud_rain.png) while it's a rain cloud. It floats up and down in code ({@link LakituCloudEntity#bob}), in
 * step with the rider's seat.
 */
public class LakituCloudRenderer<R extends LivingEntityRenderState & GeoRenderState> extends GeoEntityRenderer<LakituCloudEntity, R> {
    static final DataTicket<Boolean> RAIN_CLOUD = DataTicket.create("lakitu_rain_cloud", Boolean.class);
    private static final Identifier RAIN_TEXTURE = Lakitu.id("textures/entity/lakitu_cloud_rain.png");

    public LakituCloudRenderer(EntityRendererProvider.Context context) {
        super(context, Lakitu.cloudEntity.get());
        this.shadowRadius = 0.7F;
    }

    @Override
    public void addRenderData(LakituCloudEntity cloud, @Nullable Void relatedObject, R renderState, float partialTick) {
        renderState.addGeckolibData(RAIN_CLOUD, cloud.isRainCloud());
    }

    /** The seat moves once a tick and the rider is drawn between ticks, so the model trails by a tick to stay level. */
    @Override
    public void adjustRenderPose(RenderPassInfo<R> renderPassInfo) {
        super.adjustRenderPose(renderPassInfo);
        renderPassInfo.poseStack().translate(0.0F, LakituCloudEntity.bob(renderPassInfo.renderState().ageInTicks - 1.0F), 0.0F);
    }

    @Override
    public Identifier getTextureLocation(R renderState) {
        return Boolean.TRUE.equals(renderState.getGeckolibData(RAIN_CLOUD)) ? RAIN_TEXTURE : super.getTextureLocation(renderState);
    }
}
