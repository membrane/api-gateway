package com.predic8.membrane.core.util;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;

public final class DLPUtil {

    private static final LoggerContext CTX = (LoggerContext) LogManager.getContext(false);
    private static final String BASE = "com.predic8.membrane.core";

    private DLPUtil() {
    }

    public static void displayTraceWarning() {
        if (!isTraceEnabledSomewhere(CTX.getConfiguration())) return;

        System.out.print("""
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
        if (config.getRootLogger().getLevel() == Level.TRACE) return true;
        if (config.getLoggerConfig(BASE).getLevel() == Level.TRACE) return true;
        return config.getLoggers()
                .keySet()
                .stream()
                .filter(name -> name.startsWith(BASE + "."))
                .map(name -> config.getLoggerConfig(name).getLevel())
                .anyMatch(Level.TRACE::equals);
    }
}
