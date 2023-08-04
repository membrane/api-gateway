package com.predic8.membrane.core.transport.ssl;

import groovy.json.StringEscapeUtils;

import java.io.IOException;

import static groovy.json.StringEscapeUtils.escapeJava;

public class TLSUnrecognizedNameException extends IOException {
    public TLSUnrecognizedNameException(String hostname) {
        super("no TLS certificate configured (sending unrecognized_name alert) for hostname \"" + escapeJava(hostname) +
                "\"");
    }
}
