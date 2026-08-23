package com.excederus.shaderpatcher.patching;

import com.excederus.shaderpatcher.platform.Platform;
import com.excederus.shaderpatcher.resource.model.Shaderpack;
import org.apache.commons.io.file.PathUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static com.excederus.shaderpatcher.Constants.*;

public class ShaderpackExtractor {

    private final Platform platform;

    public ShaderpackExtractor(Platform platform) {
        this.platform = platform;
    }

    public Path extractShaderpacks(List<Shaderpack> shaderpacks) {

        Path workingDir = platform.getGameDir().resolve(".shaderpatcher");

        for (Shaderpack shaderpack : shaderpacks) {
            switch (shaderpack.type()) {
                case ZIP -> extractZip(shaderpack.name(), shaderpack.path(), workingDir);
                case FOLDER -> extractFolder(shaderpack.name(), shaderpack.path(), workingDir);
            }
        }

        try (Stream<Path> stream = Files.list(workingDir)) {
            long count = stream.filter(Files::isDirectory).count();

            if (count == 0) {
                LOG.error("No shaderpacks extracted");
                return null;
            }
        } catch (IOException e) {
            LOG.error("Failed to list files in {}", workingDir, e);
            return null;
        }

        return workingDir;
    }

    private void extractZip(String name, Path originPath, Path workingDir) {

        Path targetPath = workingDir.resolve(name);

        if (Files.exists(targetPath)) {
            LOG.warn("Working directory for {} already exists", name);
            return;
        }

        try {
            Files.createDirectories(targetPath);

            try (ZipInputStream stream = new ZipInputStream(Files.newInputStream(originPath))) {
                ZipEntry entry;

                while ((entry = stream.getNextEntry()) != null) {
                    Path out = targetPath.resolve(entry.getName()).normalize();

                    if (!out.startsWith(targetPath)) {
                        LOG.warn("Blocked suspicious zip entry {}", entry.getName());
                        continue;
                    }

                    if (entry.isDirectory())
                        Files.createDirectories(out);
                    else {
                        Files.createDirectories(out.getParent());
                        Files.copy(stream, out, StandardCopyOption.REPLACE_EXISTING);
                    }

                    stream.closeEntry();
                }
            } catch (IOException e) {
                LOG.warn("Failed to stream contents of {}", name, e);
                return;
            }

            normalizeShaderpackRoot(targetPath);
        } catch (IOException e) {
            LOG.warn("Failed to extract zip for {}", name, e);
        }
    }

    private void extractFolder(String name, Path originPath, Path workingDir) {

        Path targetPath = workingDir.resolve(name);

        if (Files.exists(targetPath)) {
            LOG.warn("Working directory for {} already exists", name);
            return;
        }

        try {
            PathUtils.copyDirectory(originPath, targetPath);
            normalizeShaderpackRoot(targetPath);
        } catch (IOException e) {
            LOG.warn("Failed to copy folder for {}", name, e);
        }
    }

    private void normalizeShaderpackRoot(Path path) {

        if (Files.exists(path.resolve("shaders"))) return;

        try (Stream<Path> children = Files.list(path)) {
            List<Path> dirs = children.filter(Files::isDirectory).toList();

            for (Path dir : dirs) {
                if (Files.exists(dir.resolve("shaders"))) {
                    moveContents(dir, path);
                    Files.delete(dir);
                    return;
                }
            }
        } catch (IOException e) {
            LOG.warn("Failed to normalize shaderpack root for {}", path.getFileName());
        }
    }

    private void moveContents(Path from, Path to) {
        try (Stream<Path> stream = Files.list(from)) {
            for (Path child : stream.toList()) {
                Path target = to.resolve(child.getFileName());

                Files.move(child, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOG.warn("Failed to move files from {} to {}", from, to, e);
        }
    }
}
