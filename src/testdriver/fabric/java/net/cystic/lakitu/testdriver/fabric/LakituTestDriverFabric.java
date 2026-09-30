package net.cystic.lakitu.testdriver.fabric;

import net.cystic.lakitu.testdriver.TestDriver;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;

public class LakituTestDriverFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> TestDriver.onMessage(message));
        ClientTickEvents.END_CLIENT_TICK.register(client -> TestDriver.tick());
    }
}
