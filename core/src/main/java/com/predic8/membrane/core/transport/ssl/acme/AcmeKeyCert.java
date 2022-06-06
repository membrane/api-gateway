package com.predic8.membrane.core.transport.ssl.acme;

import javax.net.ssl.SSLContext;

public class AcmeKeyCert {
    final String key;
    final String certs;
    final long validUntil;
    final javax.net.ssl.SSLContext sslc;

    public AcmeKeyCert(String keyS, String certsS, long validUntil, javax.net.ssl.SSLContext sslc) {
        this.key = keyS;
        this.certs = certsS;
        this.validUntil = validUntil;
        this.sslc = sslc;
    }

    public long getValidUntil() {
        return validUntil;
    }

    public SSLContext getSslContext() {
        return sslc;
    }

    public String getKey() {
        return key;
    }

    public String getCerts() {
        return certs;
    }
}