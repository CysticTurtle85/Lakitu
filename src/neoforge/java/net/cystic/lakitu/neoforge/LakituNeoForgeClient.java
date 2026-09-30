package net.cystic.lakitu.neoforge;

import net.cystic.lakitu.Lakitu;
import net.cystic.lakitu.client.LakituCloudRenderer;
import net.cystic.lakitu.client.LakituRenderer;
import net.cystic.lakitu.client.SpinyEggRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Client-only half of the mod, so dedicated servers never load renderer classes. */
@Mod(value = Lakitu.MOD_ID, dist = Dist.CLIENT)
public class LakituNeoForgeClient {
    public LakituNeoForgeClient(IEventBus modBus) {
        modBus.addListener(LakituNeoForgeClient::registerRenderers);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(Lakitu.cloudEntity.get(), context -> new LakituCloudRenderer<>(context));
        event.registerEntityRenderer(Lakitu.lakituEntity.get(), context -> new LakituRenderer<>(context));
        event.registerEntityRenderer(Lakitu.spinyEgg.get(), context -> new SpinyEggRenderer<>(context));
    }
}
