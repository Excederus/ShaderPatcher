package com.excederus.shaderpatcher;

import com.excederus.shaderpatcher.platform.ForgePlatform;
import com.excederus.shaderpatcher.Constants.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod(Constants.MODID)
@Mod.EventBusSubscriber(modid = Constants.MODID, value = Dist.CLIENT)
public class ForgeEntrypoint {

    private static boolean initialized = false;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {

        if (event.phase != TickEvent.Phase.END || initialized)
            return;

        initialized = true;

        ShaderPatcher shaderpatcher = new ShaderPatcher(new ForgePlatform());
        shaderpatcher.run();
    }
}