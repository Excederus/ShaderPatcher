package com.excederus.shaderpatcher.platform.forge;

import com.excederus.shaderpatcher.Constants;
import com.excederus.shaderpatcher.ShaderPatcher;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod(Constants.MODID)
public class NeoForgeShaderPatcher {

    public NeoForgeShaderPatcher() {
        FMLJavaModLoadingContext.get().getModEventBus()
                .addListener(this::onClientStarted);
    }

    private void onClientStarted(FMLClientSetupEvent event) {

        ShaderPatcher runtime = new ShaderPatcher(new NeoForgePlatform());

        runtime.run();
    }
}