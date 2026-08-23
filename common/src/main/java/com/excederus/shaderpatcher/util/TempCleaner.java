package com.excederus.shaderpatcher.util;

import com.excederus.shaderpatcher.platform.Platform;

import java.nio.file.Path;
import static com.excederus.shaderpatcher.util.Helpers.*;

public class TempCleaner {

    private final Platform platform;

    public TempCleaner(Platform platform) {
        this.platform = platform;
    }

    public void cleanTempFiles() {

        Path workingDir = platform.getGameDir().resolve(".shaderpatcher");

        recursiveDelete(workingDir);
    }
}
