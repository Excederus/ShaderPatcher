package com.excederus.shaderpatcher.loader.forge;

import com.excederus.shaderpatcher.HostEnvironment;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.forgespi.language.IModInfo;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ForgeHost implements HostEnvironment {
    public String loaderName()  { return "Forge"; }
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