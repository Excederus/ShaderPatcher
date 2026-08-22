package com.excederus.shaderpatcher.platform;

import com.excederus.shaderpatcher.resource.model.InternalResource;

import java.nio.file.Path;
import java.util.List;

public interface Platform {

    String getVersion();

    LoaderType getLoaderType();

    Path getGameDir();

    List<InternalResource> getInternalResources();
}
