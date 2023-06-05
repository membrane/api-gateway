package com.predic8.membrane.core.transport.ssl.acme;

import com.fasterxml.jackson.databind.JsonNode;
import com.predic8.membrane.core.azure.AzureConfig;
import com.predic8.membrane.core.azure.DnsProvisionable;
import com.predic8.membrane.core.azure.api.AzureApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

public class AcmeAzureTableApiStorageEngine implements AcmeSynchronizedStorageEngine, DnsProvisionable {

    private static final Logger log = LoggerFactory.getLogger(AcmeAzureTableApiStorageEngine.class);

    private static final String CURRENT = "current";
    private static final String CURRENT_ERROR = "current-error";
    private static final String CURRENT_KEY = "current-key";

    private final AzureApiClient apiClient;

    public AcmeAzureTableApiStorageEngine(AzureConfig azureConfig) {
        apiClient = new AzureApiClient(azureConfig);

        try {
            apiClient.tableStorage().table().create();
        } catch (Exception e) {
            // ignore if table exists already
            log.debug("Ignore table already exists exception");
        }

        log.debug("Loaded {}", this.getClass().getSimpleName());
    }

    private JsonNode getEntity(String rowKey) {
        try {
            log.debug("Get entity for {}", rowKey);
            return apiClient.tableStorage().entity(rowKey).get();
        } catch (Exception e) {
            log.debug("Entity {} does not exist, returning null", rowKey);
            return null;
        }
    }

    private String getDataPropertyOfEntity(String rowKey) {
        var entity = getEntity(rowKey);

        return entity != null
                ? entity.get("data").asText()
                : null;
    }

    private void upsertDataEntity(String rowKey, String data) {
        try {
            log.debug("Upserting key {}", rowKey);
            apiClient.tableStorage().entity(rowKey).insertOrReplace(data);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String id(String[] hosts) {
        int i = Arrays.hashCode(hosts);
        if (i < 0) i = Integer.MAX_VALUE + i + 1;
        return hosts[0] + "-" + i;
    }

    private String getPublicKeyRowKey(String[] hosts) {
        return String.format("key-%s-pub.pem", id(hosts));
    }

    private String getPrivateKeyRowKey(String[] hosts) {
        return String.format("key-%s.pem", id(hosts));
    }

    private String getCertChainRowKey(String[] hosts) {
        return String.format("cert-%s.pem", id(hosts));
    }

    private String getTokenRowKey(String host) {
        return String.format("token-%s", host);
    }

    private String getOALRowKey(String[] hosts, String postfix) {
        return String.format("oal-%s-%s.json", id(hosts), postfix);
    }

    @Override
    public String getAccountKey() {
        return getDataPropertyOfEntity("account");
    }

    @Override
    public void setAccountKey(String key) {
        upsertDataEntity("account", key);
    }

    @Override
    public void setKeyPair(String[] hosts, AcmeKeyPair key) {
        upsertDataEntity(getPublicKeyRowKey(hosts), key.getPublicKey());
        upsertDataEntity(getPrivateKeyRowKey(hosts), key.getPrivateKey());
    }

    @Override
    public String getPublicKey(String[] hosts) {
        return getDataPropertyOfEntity(getPublicKeyRowKey(hosts));
    }

    @Override
    public String getPrivateKey(String[] hosts) {
        return getDataPropertyOfEntity(getPrivateKeyRowKey(hosts));
    }

    @Override
    public void setCertChain(String[] hosts, String caChain) {
        upsertDataEntity(getCertChainRowKey(hosts), caChain);
    }

    @Override
    public String getCertChain(String[] hosts) {
        return getDataPropertyOfEntity(getCertChainRowKey(hosts));
    }

    @Override
    public void setToken(String host, String token) {
        upsertDataEntity(getTokenRowKey(host), token);
    }

    @Override
    public String getToken(String host) {
        return getDataPropertyOfEntity(getTokenRowKey(host));
    }

    @Override
    public String getOAL(String[] hosts) {
        return getDataPropertyOfEntity(getOALRowKey(hosts, CURRENT));
    }

    @Override
    public void setOAL(String[] hosts, String oal) {
        upsertDataEntity(getOALRowKey(hosts, CURRENT), oal);
    }

    @Override
    public String getAccountURL() {
        return getDataPropertyOfEntity("account-url");
    }

    @Override
    public void setAccountURL(String url) {
        upsertDataEntity("account-url", url);
    }

    @Override
    public String getAccountContacts() {
        return getDataPropertyOfEntity("account-contacts");
    }

    @Override
    public void setAccountContacts(String contacts) {
        upsertDataEntity("account-contacts", contacts);
    }

    @Override
    public String getOALError(String[] hosts) {
        return getDataPropertyOfEntity(getOALRowKey(hosts, CURRENT_ERROR));
    }

    @Override
    public void setOALError(String[] hosts, String oalError) {
        upsertDataEntity(getOALRowKey(hosts, CURRENT_ERROR), oalError);
    }

    @Override
    public String getOALKey(String[] hosts) {
        return getDataPropertyOfEntity(getOALRowKey(hosts, CURRENT_KEY));
    }

    @Override
    public void setOALKey(String[] hosts, String oalKey) {
        upsertDataEntity(getOALRowKey(hosts, CURRENT_KEY), oalKey);
    }

    @Override
    public void archiveOAL(String[] hosts) {
        long now = System.currentTimeMillis();
        attemptRename(getOALRowKey(hosts, CURRENT), getOALRowKey(hosts, String.valueOf(now)));
        attemptRename(getOALRowKey(hosts, CURRENT_ERROR), getOALRowKey(hosts, now + "-error"));
        attemptRename(getOALRowKey(hosts, CURRENT_KEY), getOALRowKey(hosts, now + "-key"));
    }

    private void attemptRename(String f1, String f2) {
        log.debug("Attempt rename {} to {}", f1, f2);
        var first = getDataPropertyOfEntity(f1);

        if (first != null) {
            try {
                log.debug("creating {}", f2);
                apiClient.tableStorage().entity(f2).insertOrReplace(first);
                log.debug("removing {}", f1);
                apiClient.tableStorage().entity(f1).delete();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return;
        }

        log.debug("Attempt rename, but there was nothing to rename");
    }

    @Override
    public void provisionDns(String domain, String record) {
        try {
            apiClient.dnsRecords().txt(domain)
                    .ttl(300)
                    .addRecord()
                        .withValue(record)
                    .create();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean acquireLease(long durationMillis) {
        // Single instance
        return true;
    }

    @Override
    public boolean prolongLease(long durationMillis) {
        // Single instance
        return true;
    }

    @Override
    public void releaseLease() {
        // Single instance
    }
}
