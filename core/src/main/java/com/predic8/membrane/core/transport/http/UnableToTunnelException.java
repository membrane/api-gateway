package com.predic8.membrane.core.transport.http;

/**
 * Connection could not be tunneled through the proxy server
 */
public class UnableToTunnelException extends RuntimeException {

    public UnableToTunnelException(String message) {
        super(message);
    }

}
