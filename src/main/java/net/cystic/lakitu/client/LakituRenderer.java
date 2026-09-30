package net.cystic.lakitu.client;

import net.cystic.lakitu.Lakitu;
import net.cystic.lakitu.entity.LakituEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/** The Lakitu mob: geckolib/models/entity/lakitu.geo.json, the Lakitu sitting on its cloud. */
public class LakituRenderer<R extends LivingEntityRenderState & GeoRenderState> extends GeoEntityRenderer<LakituEntity, R> {
    public LakituRenderer(EntityRendererProvider.Context context) {
        super(context, Lakitu.lakituEntity.get());
        this.shadowRadius = 0.6F;
    }
}
