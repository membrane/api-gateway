package com.predic8.membrane.core;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;

public class DLPUtil {

    private static final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
    private static final Configuration config = ctx.getConfiguration();

    public static void displayTraceWarning() {
        if (config.getLoggerConfig("com.predic8.membrane.core").getLevel().intLevel() <= Level.TRACE.intLevel()) {
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
    }
}
