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

/**
 * The rideable cloud: geckolib/models/entity/lakitu_cloud.geo.json, the Lakitu model without the Lakitu. Grey
 * (lakitu_cloud_rain.png) while it's a rain cloud.
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

    @Override
    public Identifier getTextureLocation(R renderState) {
        return Boolean.TRUE.equals(renderState.getGeckolibData(RAIN_CLOUD)) ? RAIN_TEXTURE : super.getTextureLocation(renderState);
    }
}
