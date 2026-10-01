package net.cystic.lakitu.client;

import net.cystic.lakitu.Lakitu;
import net.cystic.lakitu.entity.LakituCloudEntity;
import net.cystic.lakitu.flight.client.FlyingMountRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
//#if MC >= 1.21.5
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.jspecify.annotations.Nullable;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.renderer.base.GeoRenderState;
//#endif

/**
 * The rideable cloud: geckolib/models/entity/lakitu_cloud.geo.json, the Lakitu model without the Lakitu. Floating,
 * turning and leaning come from FlyingMountRenderer (it leans around its middle, 6 px up: FlightSettings); grey
 * (lakitu_cloud_rain.png) while it's a rain cloud.
 */
//#if MC >= 1.21.5
public class LakituCloudRenderer<R extends LivingEntityRenderState & GeoRenderState> extends FlyingMountRenderer<LakituCloudEntity, R> {
    static final DataTicket<Boolean> RAIN_CLOUD = DataTicket.create("lakitu_rain_cloud", Boolean.class);
//#else
public class LakituCloudRenderer<R> extends FlyingMountRenderer<LakituCloudEntity> {
//#endif
    private static final Identifier RAIN_TEXTURE = Lakitu.id("textures/entity/lakitu_cloud_rain.png");

    public LakituCloudRenderer(EntityRendererProvider.Context context) {
        super(context, Lakitu.cloudEntity.get());
        this.shadowRadius = 0.7F;
    }

    //#if MC >= 1.21.5
    @Override
    //#if MC >= 1.21.9
    public void addRenderData(LakituCloudEntity cloud, @Nullable Void relatedObject, R renderState, float partialTick) {
        super.addRenderData(cloud, relatedObject, renderState, partialTick);
    //#else
    public void addRenderData(LakituCloudEntity cloud, @Nullable Void relatedObject, R renderState) {
        super.addRenderData(cloud, relatedObject, renderState);
    //#endif
        renderState.addGeckolibData(RAIN_CLOUD, cloud.isRainCloud());
    }

    @Override
    public Identifier getTextureLocation(R renderState) {
        return Boolean.TRUE.equals(renderState.getGeckolibData(RAIN_CLOUD)) ? RAIN_TEXTURE : super.getTextureLocation(renderState);
    }
    //#else
    @Override
    public Identifier getTextureLocation(LakituCloudEntity cloud) {
        return cloud.isRainCloud() ? RAIN_TEXTURE : super.getTextureLocation(cloud);
    }
    //#endif
}
