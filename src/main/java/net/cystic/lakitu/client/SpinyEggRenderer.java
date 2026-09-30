package net.cystic.lakitu.client;

import net.cystic.lakitu.Lakitu;
import net.cystic.lakitu.entity.SpinyEggEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/** The Lakitu's spiny egg: geckolib/models/entity/spiny_egg.geo.json, spinning as it flies. */
public class SpinyEggRenderer<R extends EntityRenderState & GeoRenderState> extends GeoEntityRenderer<SpinyEggEntity, R> {
    public SpinyEggRenderer(EntityRendererProvider.Context context) {
        super(context, Lakitu.spinyEgg.get());
        this.shadowRadius = 0.15F;
    }
}
