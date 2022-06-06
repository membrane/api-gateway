package com.predic8.membrane.core.transport.ssl.acme;

import java.util.Arrays;
import java.util.HashMap;

public class AcmeMemoryStorageEngine implements AcmeSynchronizedStorageEngine {

    public AcmeMemoryStorageEngine() {}

    private HashMap<String, String> files = new HashMap<>();
    private long lockedUntil = 0;

    @Override
    public synchronized String getAccountKey() {
        return files.get("account.jwk.json");
    }

    @Override
    public synchronized void setAccountKey(String key) {
        files.put("account.jwk.json", key);
    }

    @Override
    public synchronized void setKeyPair(String[] hosts, AcmeKeyPair key) {
        files.put("key-" + id(hosts) + "-pub.pem", key.getPublicKey());
        files.put("key-" + id(hosts) + ".pem", key.getPrivateKey());
    }

    @Override
    public synchronized String getPublicKey(String[] hosts) {
        return files.get("key-" + id(hosts) + "-pub.pem");
    }

    @Override
    public synchronized String getPrivateKey(String[] hosts) {
        return files.get("key-" + id(hosts) + ".pem");
    }

    @Override
    public synchronized void setCertChain(String[] hosts, String caChain) {
        files.put("cert-" + id(hosts) + ".pem", caChain);
    }

    @Override
    public synchronized String getCertChain(String[] hosts) {
        return files.get("cert-" + id(hosts) + ".pem");
    }

    private synchronized String id(String[] hosts) {
        int i = Arrays.hashCode(hosts);
        if (i < 0)
            i = Integer.MAX_VALUE + i + 1;
        return hosts[0] + "-" + i;
    }

    @Override
    public synchronized void setToken(String host, String token) {
        files.put("token-" + host + ".txt", token);
    }

    @Override
    public synchronized String getToken(String host) {
        return files.get("token-" + host + ".txt");
    }

    @Override
    public synchronized String getAccountURL() {
        return files.get("account-url.txt");
    }

    @Override
    public synchronized void setAccountURL(String url) {
        files.put("account-url.txt", url);
    }

    @Override
    public synchronized String getAccountContacts() {
        return files.get("account-contacts.txt");
    }

    @Override
    public synchronized void setAccountContacts(String contacts) {
        files.put("account-contacts.txt", contacts);
    }

    @Override
    public synchronized String getOAL(String[] hosts) {
        return files.get("oal-"+id(hosts)+"-current.json");
    }

    @Override
    public synchronized void setOAL(String[] hosts, String oal) {
        files.put("oal-"+id(hosts)+"-current.json", oal);
    }

    @Override
    public synchronized String getOALError(String[] hosts) {
        return files.get("oal-"+id(hosts)+"-current-error.json");
    }

    @Override
    public synchronized void setOALError(String[] hosts, String oalError) {
        files.put("oal-"+id(hosts)+"-current-error.json", oalError);
    }

    @Override
    public synchronized String getOALKey(String[] hosts) {
        return files.get("oal-"+id(hosts)+"-current-key.json");
    }

    @Override
    public synchronized void setOALKey(String[] hosts, String oalKey) {
        files.put("oal-"+id(hosts)+"-current-key.json", oalKey);
    }

    @Override
    public synchronized void archiveOAL(String[] hosts) {
        long now = System.currentTimeMillis();
        String id = id(hosts);
        attemptRename("oal-"+id+"-current.json", "oal-"+id+"-" + now + ".json");
        attemptRename("oal-"+id+"-current-error.json", "oal-"+id+"-" + now + "-error.json");
        attemptRename("oal-"+id+"-current-key.json", "oal-"+id+"-" + now + "-key.json");
    }

    private void attemptRename(String key1, String key2) {
        String oldValue = files.remove(key1);
        if (oldValue != null)
            files.put(key2, oldValue);
    }

    @Override
    public synchronized boolean acquireLease(long durationMillis) {
        if (lockedUntil != 0)
            return false;
        lockedUntil = System.currentTimeMillis() + durationMillis;
        return true;
    }

    @Override
    public synchronized boolean prolongLease(long durationMillis) {
        if (lockedUntil < System.currentTimeMillis())
            return false;
        lockedUntil = System.currentTimeMillis() + durationMillis;
        return true;
    }

    @Override
    public synchronized void releaseLease() {
        lockedUntil = 0;
    }
}
