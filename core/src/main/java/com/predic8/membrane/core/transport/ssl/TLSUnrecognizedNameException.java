package com.predic8.membrane.core.transport.ssl;

import java.io.IOException;

public class TLSUnrecognizedNameException extends IOException {
    public TLSUnrecognizedNameException(String hostname) {
        super("no TLS certificate configured (sending unrecognized_name alert) for hostname \"" + hostname + "\"");
    }
}
