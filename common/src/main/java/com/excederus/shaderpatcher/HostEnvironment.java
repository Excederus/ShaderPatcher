package com.excederus.shaderpatcher;

import java.nio.file.Path;
import java.util.List;

public interface HostEnvironment {
    String loaderName();
    Path gameDir();
    Path configDir();
    List<ModSource> mods();

    record ModSource(String modId, String version, Path path) {}
}