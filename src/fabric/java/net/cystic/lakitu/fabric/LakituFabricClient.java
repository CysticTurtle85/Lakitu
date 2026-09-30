package net.cystic.lakitu.fabric;

import net.cystic.lakitu.Lakitu;
import net.cystic.lakitu.client.LakituCloudRenderer;
import net.cystic.lakitu.client.LakituRenderer;
import net.cystic.lakitu.client.SpinyEggRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class LakituFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(Lakitu.cloudEntity.get(), context -> new LakituCloudRenderer<>(context));
        EntityRendererRegistry.register(Lakitu.lakituEntity.get(), context -> new LakituRenderer<>(context));
        EntityRendererRegistry.register(Lakitu.spinyEgg.get(), context -> new SpinyEggRenderer<>(context));
    }
}
