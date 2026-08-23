package com.excederus.shaderpatcher.patching;

import com.excederus.shaderpatcher.platform.Platform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.excederus.shaderpatcher.Constants.*;

public class ShaderpackImporter {

    private final Platform platform;

    public ShaderpackImporter(Platform platform) {
        this.platform = platform;
    }

    public void importShaderpacks(Path workingDir) {

        List<Path> workingPaths;

        try (Stream<Path> stream = Files.list(workingDir)) {
            workingPaths = stream.filter(Files::isDirectory).filter(path -> !path.equals(workingDir)).toList();
        } catch (IOException e) {
            LOG.error("Failed to list extracted shaderpacks in {}", workingDir, e);
            return;
        }

        for (Path workingPath : workingPaths) {
            String name = workingPath.getFileName().toString();
            Path outputPath = platform.getGameDir().resolve("shaderpacks").resolve(name + " + " + MODTAG + ".zip");

            zipShader(workingPath, outputPath);
        }
    }

    private void zipShader(Path workingPath, Path outputPath) {

        AtomicInteger filesFailed = new AtomicInteger(0);

        try (ZipOutputStream outputStream = new ZipOutputStream(Files.newOutputStream(outputPath))) {
            try (Stream<Path> inputStream = Files.walk(workingPath)) {
                inputStream.filter(Files::isRegularFile).forEach(file -> {
                    try {
                        ZipEntry entry = new ZipEntry(workingPath.relativize(file).toString().replace("\\", "/"));

                        try {
                            outputStream.putNextEntry(entry);
                            Files.copy(file, outputStream);
                        } finally {
                            outputStream.closeEntry();
                        }
                    } catch (IOException e) {
                        LOG.warn("Failed to add {} to zip", file, e);
                        filesFailed.getAndIncrement();
                    }
                });
            } catch (IOException e) {
                LOG.warn("Failed to walk files in {}", workingPath, e);
                return;
            }
        } catch (IOException e) {
            LOG.warn("Failed to create zip stream for {}", workingPath.getFileName(), e);
            return;
        }

        if (filesFailed.get() > 0) {
            LOG.warn("Failed to add {} files to {} - deleting corrupted zip", filesFailed.get(), outputPath);
            try {
                Files.deleteIfExists(outputPath);
            } catch (IOException e) {
                LOG.warn("Failed to delete corrupted zip {}", outputPath, e);
            }
        }
    }
}
