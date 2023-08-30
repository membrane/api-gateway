package com.predic8.membrane.core.transport.ssl;

import java.io.*;

import static groovy.json.StringEscapeUtils.*;

public class TLSUnrecognizedNameException extends IOException {
    public TLSUnrecognizedNameException(String hostname) {
        super("no TLS certificate configured (sending unrecognized_name alert) for hostname \"" + escapeJava(hostname) +
                "\"");
    }
}
