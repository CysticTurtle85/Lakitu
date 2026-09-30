package net.cystic.lakitu.client;

import net.cystic.lakitu.Lakitu;
import net.cystic.lakitu.entity.LakituCloudEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/** The rideable cloud: geckolib/models/entity/lakitu_cloud.geo.json, the Lakitu model without the Lakitu. */
public class LakituCloudRenderer<R extends LivingEntityRenderState & GeoRenderState> extends GeoEntityRenderer<LakituCloudEntity, R> {
    public LakituCloudRenderer(EntityRendererProvider.Context context) {
        super(context, Lakitu.cloudEntity.get());
        this.shadowRadius = 0.7F;
    }
}
