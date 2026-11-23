package com.excederus.shaderpatcher.loader.neoforge;

import com.excederus.shaderpatcher.Bootstrap;
import net.neoforged.fml.common.Mod;

@Mod("shaderpatcher")
public final class NeoForgeEntrypoint {
    public NeoForgeEntrypoint() {
        try {
            Bootstrap.start(new NeoForgeHost());
        } catch (Throwable t) {
            System.out.println("[ShaderPatcher] could not start on neoforge: " + t);
        }
    }
}