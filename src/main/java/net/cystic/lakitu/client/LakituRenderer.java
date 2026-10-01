package net.cystic.lakitu.client;

import net.cystic.lakitu.Lakitu;
import net.cystic.lakitu.entity.LakituEntity;
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
 * cloud is grey (lakitu_rain.png) while it's a rain cloud.
 */
public class LakituRenderer<R extends LivingEntityRenderState & GeoRenderState> extends GeoEntityRenderer<LakituEntity, R> {
    private static final DataTicket<Boolean> THROWING = DataTicket.create("lakitu_throwing", Boolean.class);
    private static final Identifier RAIN_TEXTURE = Lakitu.id("textures/entity/lakitu_rain.png");

    public LakituRenderer(EntityRendererProvider.Context context) {
        super(context, Lakitu.lakituEntity.get());
        this.shadowRadius = 0.7F;
    }

    @Override
    public void addRenderData(LakituEntity lakitu, @Nullable Void relatedObject, R renderState, float partialTick) {
        renderState.addGeckolibData(THROWING, lakitu.isThrowing());
        renderState.addGeckolibData(LakituCloudRenderer.RAIN_CLOUD, lakitu.isRainCloud());
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
