package net.cystic.lakitu.client;

import net.cystic.lakitu.Lakitu;
import net.cystic.lakitu.entity.SpinyEggEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
//#if MC >= 1.21.5
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import software.bernie.geckolib.renderer.base.GeoRenderState;
//#endif
//#if MC < 1.21.9
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
//#endif

/** The Lakitu's spiny egg: geckolib/models/entity/spiny_egg.geo.json, spinning as it flies. */
//#if MC >= 1.21.5
public class SpinyEggRenderer<R extends EntityRenderState & GeoRenderState> extends GeoEntityRenderer<SpinyEggEntity, R> {
    public SpinyEggRenderer(EntityRendererProvider.Context context) {
        //#if MC >= 1.21.9
        super(context, Lakitu.spinyEgg.get());
        //#else
        super(context, new DefaultedEntityGeoModel<>(Lakitu.id("spiny_egg")));
        //#endif
        this.shadowRadius = 0.15F;
    }
}
//#else
public class SpinyEggRenderer<R> extends GeoEntityRenderer<SpinyEggEntity> {
    public SpinyEggRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(Lakitu.id("spiny_egg")));
        this.shadowRadius = 0.15F;
    }
}
//#endif
