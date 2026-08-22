package com.excederus.shaderpatcher.platform.fabric;

import com.excederus.shaderpatcher.ShaderPatcher;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

public class FabricShaderPatcher implements ClientModInitializer {

  @Override
  public void onInitializeClient() {

    ClientLifecycleEvents.CLIENT_STARTED.register(client -> {

      ShaderPatcher runtime = new ShaderPatcher(new FabricPlatform());

      runtime.run();
    });
  }
}
