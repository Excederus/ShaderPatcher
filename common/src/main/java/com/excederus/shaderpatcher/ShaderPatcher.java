package com.excederus.shaderpatcher;

import com.excederus.shaderpatcher.patching.ShaderpackExtractor;
import com.excederus.shaderpatcher.patching.ShaderpackImporter;
import com.excederus.shaderpatcher.patching.ShaderpackPatcher;
import com.excederus.shaderpatcher.platform.Platform;
import com.excederus.shaderpatcher.resource.ResourceLoader;
import com.excederus.shaderpatcher.resource.ResourceParser;
import com.excederus.shaderpatcher.resource.model.RawResourceBundle;
import com.excederus.shaderpatcher.resource.model.ResourceBundle;
import com.excederus.shaderpatcher.util.InitialSetup;
import com.excederus.shaderpatcher.util.TempCleaner;

import java.nio.file.Path;

import static com.excederus.shaderpatcher.Constants.*;

public class ShaderPatcher {

    private final Platform platform;

    public ShaderPatcher(Platform platform) {
        this.platform = platform;
    }

    public void run() {

        InitialSetup setup = new InitialSetup(platform);
        ResourceLoader loader = new ResourceLoader(platform);
        ResourceParser parser = new ResourceParser();
        ShaderpackExtractor extractor = new ShaderpackExtractor(platform);
        ShaderpackPatcher patcher = new ShaderpackPatcher();
        ShaderpackImporter importer = new ShaderpackImporter(platform);
        TempCleaner cleaner = new TempCleaner(platform);

        boolean setupSuccess = setup.setup();
        if (!setupSuccess)
            return;

        try {
            RawResourceBundle rawResourceBundle = loader.loadResources();
            if (rawResourceBundle == null)
                return;

            ResourceBundle resourceBundle = parser.parseResources(rawResourceBundle);
            if (resourceBundle == null)
                return;

            LOG.info("Successfully collected {} shaderpacks, {} patches, {} mappings, {} transforms", resourceBundle.shaderpacks().size(), resourceBundle.patches().size(), resourceBundle.mappings().size(), resourceBundle.transforms().size());

            Path workspace = extractor.extractShaderpacks(resourceBundle.shaderpacks());
            if (workspace == null)
                return;

            boolean patched = patcher.patchShaderpacks(workspace, resourceBundle);
            if (!patched)
                return;

            importer.importShaderpacks(workspace);
        } finally {
            cleaner.cleanTempFiles();
        }
    }
}
