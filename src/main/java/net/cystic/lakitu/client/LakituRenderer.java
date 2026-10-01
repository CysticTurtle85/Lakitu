package net.cystic.lakitu.client;

import net.cystic.lakitu.Lakitu;
import net.cystic.lakitu.entity.LakituEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.jspecify.annotations.Nullable;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.base.BoneSnapshots;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.base.RenderPassInfo;

/**
 * The Lakitu mob: geckolib/models/entity/lakitu.geo.json, the Lakitu sitting on its cloud. The spiny egg in its right
 * hand ("held_egg") is only drawn while the throw animation plays; the animation itself scales it in and out.
 */
public class LakituRenderer<R extends LivingEntityRenderState & GeoRenderState> extends GeoEntityRenderer<LakituEntity, R> {
    private static final DataTicket<Boolean> THROWING = DataTicket.create("lakitu_throwing", Boolean.class);

    public LakituRenderer(EntityRendererProvider.Context context) {
        super(context, Lakitu.lakituEntity.get());
        this.shadowRadius = 0.7F;
    }

    @Override
    public void addRenderData(LakituEntity lakitu, @Nullable Void relatedObject, R renderState, float partialTick) {
        renderState.addGeckolibData(THROWING, lakitu.isThrowing());
    }

    @Override
    public void adjustModelBonesForRender(RenderPassInfo<R> renderPassInfo, BoneSnapshots snapshots) {
        boolean throwing = Boolean.TRUE.equals(renderPassInfo.getGeckolibData(THROWING));
        snapshots.get("held_egg").ifPresent(egg -> egg.skipRender(!throwing));
    }
}
