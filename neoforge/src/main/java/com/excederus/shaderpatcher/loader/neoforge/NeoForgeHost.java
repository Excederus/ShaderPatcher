package com.excederus.shaderpatcher.loader.neoforge;

import com.excederus.shaderpatcher.HostEnvironment;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforgespi.language.IModInfo;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class NeoForgeHost implements HostEnvironment {
    public String loaderName()  { return "NeoForge"; }
    public Path gameDir()       { return FMLPaths.GAMEDIR.get(); }
    public Path configDir()     { return FMLPaths.CONFIGDIR.get(); }
    public List<ModSource> mods() {
        List<ModSource> modSources = new ArrayList<>();
        for (IModInfo info : ModList.get().getMods()) {
            modSources.add(new ModSource(
                    info.getModId(),
                    info.getVersion().toString(),
                    info.getOwningFile().getFile().getFilePath()));
        }
        return modSources;
    }
}