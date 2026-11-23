package com.excederus.shaderpatcher.loader.fabric;

import com.excederus.shaderpatcher.Bootstrap;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

public final class FabricEntrypoint implements PreLaunchEntrypoint {
    @Override
    public void onPreLaunch() {
        Bootstrap.start(new FabricHost());
    }
}