package com.excederus.shaderpatcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Loggers named {@code ShaderPatcher.<ClassName>} instead of the full package, to keep log prefixes short and recognizable. */
public final class Log {

    private Log() {}

    public static Logger of(Class<?> type) {
        return LoggerFactory.getLogger("ShaderPatcher." + type.getSimpleName());
    }
}
