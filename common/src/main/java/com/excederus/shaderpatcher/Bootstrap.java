package com.excederus.shaderpatcher;

import com.excederus.shaderpatcher.resource.ResourceDiscovery;
import com.excederus.shaderpatcher.resource.ResourceParser;
import com.excederus.shaderpatcher.resource.model.Kind;
import com.excederus.shaderpatcher.resource.model.Resource;
import com.excederus.shaderpatcher.shaderpack.Shaderpack;
import com.excederus.shaderpatcher.shaderpack.ShaderpackScanner;
import com.excederus.shaderpatcher.shaderpack.ShaderpackWriter;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class Bootstrap {

    private static final Logger LOG = Log.of(Bootstrap.class);

    private Bootstrap() {}

    public static void start(HostEnvironment env) {
        try {
            run(env);
        } catch (Throwable t) {
            LOG.error("ShaderPatcher failed on {}", env.loaderName(), t);
        }
    }

    private static void run(HostEnvironment env) throws IOException {

        List<HostEnvironment.ModSource> mods = env.mods();
        LOG.info("ShaderPatcher {} starting on {} ({} mod(s) detected)", Constants.MOD_VERSION, env.loaderName(), mods.size());

        Path shaderpacksDir = env.gameDir().resolve("shaderpacks");

        List<Shaderpack> shaderpacks = ShaderpackScanner.scan(shaderpacksDir);
        if (shaderpacks.isEmpty()) {
            LOG.info("No shaderpacks found, nothing to do");
            return;
        }

        LOG.info("Found {} shaderpack(s)", shaderpacks.size());

        Path configDir = env.configDir().resolve("shaderpatcher");
        Files.createDirectories(configDir.resolve("patches"));
        Files.createDirectories(configDir.resolve("mappings"));
        Files.createDirectories(configDir.resolve("transforms"));

        List<Resource> patches = resolve(Kind.PATCH, mods, configDir);
        List<Resource> mappings = resolve(Kind.MAPPING, mods, configDir);
        List<Resource> transforms = resolve(Kind.TRANSFORM, mods, configDir);

        int regenerated = 0, upToDate = 0, noPatches = 0, inUse = 0, failed = 0;

        for (Shaderpack shaderpack : shaderpacks) {
            try {
                ShaderpackWriter.Outcome outcome = ShaderpackWriter.write(
                        shaderpack.path(), shaderpack.format(), shaderpacksDir, shaderpack.name(), Constants.MOD_VERSION, patches, mappings, transforms);

                switch (outcome) {
                    case REGENERATED -> regenerated++;
                    case UP_TO_DATE -> upToDate++;
                    case NO_APPLICABLE_PATCHES -> noPatches++;
                    case IN_USE -> inUse++;
                }
            } catch (IOException e) {
                failed++;
                LOG.error("Failed to patch {}", shaderpack.name(), e);
            }
        }

        LOG.info("Finished: {} patched, {} up to date, {} with no applicable patches, {} in use, {} failed (out of {} shaderpack(s))",
                regenerated, upToDate, noPatches, inUse, failed, shaderpacks.size());
    }

    private static List<Resource> resolve(Kind kind, List<HostEnvironment.ModSource> mods, Path configDir) {
        return ResourceParser.resolveOverrides(ResourceDiscovery.discover(kind, Constants.MOD_ID, mods, configDir));
    }
}
