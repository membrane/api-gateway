package com.predic8.membrane.core.transport.ssl.acme;

/**
 * An exception causing the current ACME order to become unusable.
 */
public class FatalAcmeException extends Exception {
    public FatalAcmeException() {
    }

    public FatalAcmeException(String message) {
        super(message);
    }

    public FatalAcmeException(String message, Throwable cause) {
        super(message, cause);
    }

    public FatalAcmeException(Throwable cause) {
        super(cause);
    }
}
