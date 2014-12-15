package org.slf4j.impl;

import org.slf4j.ILoggerFactory;
import org.slf4j.spi.LoggerFactoryBinder;

/**
 * StaticLoggerBinder
 *
 * @author Florian Frankenberger
 */
public class StaticLoggerBinder implements LoggerFactoryBinder {

    private final ILoggerFactory factory = new OSGiLogFactory();
    private final String factoryString = factory.getClass().getName();

    @Override
    public ILoggerFactory getLoggerFactory() {
        return factory;
    }

    @Override
    public String getLoggerFactoryClassStr() {
        return factoryString;
    }

    /**
     * The unique instance of this class.
     */
    private static final StaticLoggerBinder SINGLETON
        = new StaticLoggerBinder();

    /**
     * Return the singleton of this class.
     *
     * @return the StaticLoggerBinder singleton
     */
    public static StaticLoggerBinder getSingleton() {
        return SINGLETON;
    }


    /**
     * Declare the version of the SLF4J API this implementation is
     * compiled against. The value of this field is usually modified
     * with each release.
     */
    // To avoid constant folding by the compiler,
    // this field must *not* be final
    public static String REQUESTED_API_VERSION = "1.7";  // !final

}
