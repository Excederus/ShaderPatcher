package com.excederus.shaderpatcher.platform.forge;

import com.excederus.shaderpatcher.Constants;
import com.excederus.shaderpatcher.ShaderPatcher;

@Mod(Constants.MODID)
public class ForgeShaderPatcher {

    public ForgeShaderPatcher() {
        MinecraftForge.EVENT_BUS.addListener(this::onClientStarted);
    }

    private void onClientStarted(ClientStartedEvent event) {
        ShaderPatcher runtime = new ShaderPatcher(new ForgePlatform());
        runtime.run();
    }
}
