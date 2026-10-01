package net.cystic.lakitu.testdriver.forge;

import net.cystic.lakitu.testdriver.TestDriver;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

/** Forge (1.20.1) hookup of the dev-only test driver: system messages in, a tick at the end of each client tick. */
@Mod("lakitu_testdriver")
public class LakituTestDriverForge {
    public LakituTestDriverForge() {
        if (FMLEnvironment.dist != Dist.CLIENT)
            return;
        MinecraftForge.EVENT_BUS.addListener((ClientChatReceivedEvent event) -> {
            if (event.isSystem())
                TestDriver.onMessage(event.getMessage());
        });
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END)
                TestDriver.tick();
        });
    }
}
