package com.excederus.shaderpatcher;

import com.excederus.shaderpatcher.platform.NeoForgePlatform;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.lifecycle.ClientStartedEvent;

@Mod(value = Constants.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = Constants.MODID, value = Dist.CLIENT)
public class NeoForgeEntrypoint {

    @SubscribeEvent
    static void onClientStarted(ClientStartedEvent event) {

        ShaderPatcher shaderpatcher = new ShaderPatcher(new NeoForgePlatform());
        shaderpatcher.run();
    }
}
