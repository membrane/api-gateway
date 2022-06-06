package com.predic8.membrane.core.transport.ssl.acme;

public class AcmeKeyPair {
    String publicKey;
    String privateKey;

    public AcmeKeyPair() {
    }

    public AcmeKeyPair(String publicKey, String privateKey) {
        this.publicKey = publicKey;
        this.privateKey = privateKey;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }
}
