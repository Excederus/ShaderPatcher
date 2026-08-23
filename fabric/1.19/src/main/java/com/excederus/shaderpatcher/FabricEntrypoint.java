package com.excederus.shaderpatcher;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

public class FabricEntrypoint implements ClientModInitializer {

    @Override
    public void onInitializeClient() {

        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {

            ShaderPatcher shaderpatcher = new ShaderPatcher(new FabricPlatform());

            shaderpatcher.run();
        });
    }
}
