package com.predic8.membrane.core.transport.ssl.acme;

import com.azure.data.tables.TableClient;
import com.azure.data.tables.TableClientBuilder;
import com.azure.data.tables.models.TableEntity;
import com.azure.data.tables.models.TableServiceException;
import com.predic8.membrane.core.config.security.acme.AzureTableStorage;

import java.util.Arrays;
import java.util.List;

public class AcmeAzureTableApiStorageEngine implements AcmeSynchronizedStorageEngine {

    private final TableClient tableClient;
    private static final String TABLE_NAME = "membrane";
    private static final String PARTITION_NAME = "acme";


    public AcmeAzureTableApiStorageEngine(AzureTableStorage tableClient) {
        this.tableClient = new TableClientBuilder()
                .connectionString(tableClient.getConnectionString())
                .tableName(TABLE_NAME)
                .buildClient();

        try {
            this.tableClient.createTable();
        } catch (TableServiceException ignore) {
            // Ignore, table already exists
        }
    }

    private TableEntity getEntity(String rowKey) {
        try {
            return tableClient.getEntity(PARTITION_NAME, rowKey);
        } catch (TableServiceException e) {
            if (e.getResponse().getStatusCode() == 404) {
                return null;
            }
            throw e;
        }
    }

    private String getDataPropertyOfEntity(String rowKey) {
        var entity = getEntity(rowKey);
        return entity != null
                ? entity.getProperty("data").toString()
                : null;
    }

    private void createDataEntity(String rowKey, Object data) {
        tableClient.createEntity(new TableEntity(PARTITION_NAME, rowKey)
                .addProperty("data", data)
        );
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
        createDataEntity("account", List.of(key));
    }

    @Override
    public void setKeyPair(String[] hosts, AcmeKeyPair key) {
        createDataEntity(getPublicKeyRowKey(hosts), List.of(key.getPublicKey()));
        createDataEntity(getPrivateKeyRowKey(hosts), List.of(key.getPrivateKey()));
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
        createDataEntity(getCertChainRowKey(hosts), List.of(caChain));
    }

    @Override
    public String getCertChain(String[] hosts) {
        return getDataPropertyOfEntity(getCertChainRowKey(hosts));
    }

    @Override
    public void setToken(String host, String token) {
        createDataEntity(getTokenRowKey(host), List.of(token));
    }

    @Override
    public String getToken(String host) {
        return getDataPropertyOfEntity(getTokenRowKey(host));
    }

    @Override
    public String getOAL(String[] hosts) {
        return getDataPropertyOfEntity(getOALRowKey(hosts, "current"));
    }

    @Override
    public void setOAL(String[] hosts, String oal) {
        createDataEntity(getOALRowKey(hosts, "current"), List.of(oal));
    }

    @Override
    public String getAccountURL() {
        return getDataPropertyOfEntity("account-url");
    }

    @Override
    public void setAccountURL(String url) {
        createDataEntity("account-url", List.of(url));
    }

    @Override
    public String getAccountContacts() {
        return getDataPropertyOfEntity("account-contacts");
    }

    @Override
    public void setAccountContacts(String contacts) {
        createDataEntity("account-contacts", List.of(contacts));
    }

    @Override
    public String getOALError(String[] hosts) {
        return getDataPropertyOfEntity(getOALRowKey(hosts, "current-error"));
    }

    @Override
    public void setOALError(String[] hosts, String oalError) {
        createDataEntity(getOALRowKey(hosts, "current-error"), List.of(oalError));
    }

    @Override
    public String getOALKey(String[] hosts) {
        return getDataPropertyOfEntity(getOALRowKey(hosts, "current-key"));
    }

    @Override
    public void setOALKey(String[] hosts, String oalKey) {
        createDataEntity(getOALRowKey(hosts, "current-key"), List.of(oalKey));
    }

    @Override
    public void archiveOAL(String[] hosts) {
        long now = System.currentTimeMillis();
        attemptRename(getOALRowKey(hosts, "current"), getOALRowKey(hosts, String.valueOf(now)));
        attemptRename(getOALRowKey(hosts, "current-error"), getOALRowKey(hosts, now + "-error"));
        attemptRename(getOALRowKey(hosts, "current-key"), getOALRowKey(hosts, now + "-key"));
    }

    private void attemptRename(String f1, String f2) {
        var first = getEntity(f1);

        if (first != null) {
            var updatedEntity = new TableEntity(PARTITION_NAME, f2)
                    .addProperty("data", first.getProperty("data"));
            tableClient.createEntity(updatedEntity);
            tableClient.deleteEntity(first);
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
