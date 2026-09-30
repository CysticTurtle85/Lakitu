package net.cystic.lakitu.testdriver.neoforge;

import net.cystic.lakitu.testdriver.TestDriver;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = "lakitu_testdriver", dist = Dist.CLIENT)
public class LakituTestDriverNeoForge {
    public LakituTestDriverNeoForge() {
        NeoForge.EVENT_BUS.addListener((ClientChatReceivedEvent.System event) -> TestDriver.onMessage(event.getMessage()));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> TestDriver.tick());
    }
}
