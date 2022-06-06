package com.predic8.membrane.core.transport.ssl.acme;

public interface AcmeSynchronizedStorageEngine {
    String getAccountKey();
    void setAccountKey(String key);

    void setKeyPair(String[] hosts, AcmeKeyPair key);
    String getPublicKey(String[] hosts);
    String getPrivateKey(String[] hosts);

    void setCertChain(String[] hosts, String caChain);
    String getCertChain(String[] hosts);

    void setToken(String host, String token);
    String getToken(String host);

    String getOAL(String[] hosts);
    void setOAL(String[] hosts, String oal);

    String getAccountURL();

    void setAccountURL(String url);

    String getAccountContacts();

    void setAccountContacts(String contacts);

    String getOALError(String[] hosts);
    void setOALError(String[] hosts, String oalError);

    String getOALKey(String[] hosts);
    void setOALKey(String[] hosts, String oalKey);

    void archiveOAL(String[] hosts);

    boolean acquireLease(long durationMillis);

    boolean prolongLease(long durationMillis);

    void releaseLease();
}
