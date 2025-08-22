package com.predic8.membrane.core.util;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;

/**
 * Data Loss Prevention
 */
public final class DLPUtil {

    private static final Logger log = LogManager.getLogger(DLPUtil.class);
    private static final String BASE = "com.predic8";

    public static void displayTraceWarning() {
        try {
            displayTraceWarning((LoggerContext) LogManager.getContext(false));
        } catch (Exception e) {
            log.warn("Log may contain sensitive data. Make sure there is no logger with level TRACE in log configuration.", e);
        }
    }

    private static void displayTraceWarning(LoggerContext ctx) {
        if (!isTraceEnabledSomewhere(ctx.getConfiguration())) return;
        log.warn("""
                ================================================================================================
                
                WARNING: TRACE logging is enabled for com.predic8.membrane.core (or one of its subpackages).
                
                TRACE-level logging may expose sensitive data including:
                - Request and response headers (including Authorization headers)
                - Request and response bodies
                - Internal processing details
                - Configuration values
                
                This should only be used in development environments or for debugging purposes.
                Ensure sensitive data is not logged in production environments.
                
                To disable TRACE, set the level to INFO or higher for 'com.predic8.membrane.core'
                in your logging configuration (e.g., log4j2.xml). Example:
                <Logger name="com.predic8.membrane.core" level="info" additivity="false"/>
                
                ================================================================================================
                """);
    }

    private static boolean isTraceEnabledSomewhere(Configuration config) {

        // Root logger defines the default level for all loggers
        if (isTraceOrAll(config.getRootLogger().getLevel())) return true;

        if (isTraceOrAll(config.getLoggerConfig(BASE).getLevel())) return true;

        return config.getLoggers()
                .entrySet()
                .stream()
                .filter(e -> {
                    String name = e.getKey();
                    return name.startsWith(BASE + ".") || BASE.startsWith(name);
                })
                .map(e -> e.getValue().getLevel())
                .anyMatch(DLPUtil::isTraceOrAll);
    }

    private static boolean isTraceOrAll(Level level) {
        return level == Level.TRACE || level == Level.ALL;
    }
}
