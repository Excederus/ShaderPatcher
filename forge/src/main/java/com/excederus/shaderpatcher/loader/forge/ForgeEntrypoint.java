package com.excederus.shaderpatcher.loader.forge;

import com.excederus.shaderpatcher.Bootstrap;
import net.minecraftforge.fml.common.Mod;

@Mod("shaderpatcher")
public final class ForgeEntrypoint {
    public ForgeEntrypoint() {
        Bootstrap.start(new ForgeHost());
    }
}