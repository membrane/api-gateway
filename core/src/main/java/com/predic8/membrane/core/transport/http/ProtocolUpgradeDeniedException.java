package com.predic8.membrane.core.transport.http;

public class ProtocolUpgradeDeniedException extends Exception {
    private final String protocol;

    public ProtocolUpgradeDeniedException(String protocol) {
        super("Unsupported protocol upgrade request. protocol=" + protocol);
        this.protocol = protocol;
    }

    public String getProtocol() {
        return protocol;
    }
}
