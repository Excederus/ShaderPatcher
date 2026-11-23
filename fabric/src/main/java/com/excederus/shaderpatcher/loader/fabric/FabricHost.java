package com.excederus.shaderpatcher.loader.fabric;

import com.excederus.shaderpatcher.HostEnvironment;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class FabricHost implements HostEnvironment {
    public String loaderName()  { return "Fabric"; }
    public Path gameDir()       { return FabricLoader.getInstance().getGameDir(); }
    public Path configDir()     { return FabricLoader.getInstance().getConfigDir(); }
    public List<ModSource> mods() {
        List<ModSource> modSources = new ArrayList<>();
        for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
            modSources.add(new ModSource(
                    container.getMetadata().getId(),
                    container.getMetadata().getVersion().getFriendlyString(),
                    container.getOrigin().getPaths().get(0)));
        }
        return modSources;
    }
}