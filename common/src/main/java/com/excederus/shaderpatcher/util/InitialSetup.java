package com.excederus.shaderpatcher.util;

import com.excederus.shaderpatcher.platform.Platform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.excederus.shaderpatcher.Constants.*;

public class InitialSetup {

    private final Platform platform;

    public InitialSetup(Platform platform) {
        this.platform = platform;
    }

    public boolean setup() {

        boolean configSuccess = true;
        boolean workingSuccess = true;
        boolean shaderpacksSuccess = true;

        Path config = platform.getGameDir().resolve("config").resolve("shaderpatcher");
        Path working = platform.getGameDir().resolve(".shaderpatcher");
        Path shaderpacks = platform.getGameDir().resolve("shaderpacks");

        if (Files.notExists(config))
            configSuccess = createConfig(config);

        if (Files.notExists(working))
            workingSuccess = createSingle(working);

        if (Files.notExists(shaderpacks))
            shaderpacksSuccess = createSingle(shaderpacks);

        return configSuccess && workingSuccess && shaderpacksSuccess;
    }

    private boolean createConfig(Path config) {

        try {
            Files.createDirectories(config.resolve("patches"));
            Files.createDirectories(config.resolve("mappings"));
            Files.createDirectories(config.resolve("transforms"));
        } catch (IOException e) {
            LOG.error("Failed to create config directories", e);
            return false;
        }

        return true;
    }

    private boolean createSingle(Path dir) {

        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            LOG.error("Failed to create {} directory", dir, e);
            return false;
        }

        return true;
    }
}
