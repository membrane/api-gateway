package com.predic8.membrane.core.transport.ssl.acme;

import com.azure.data.tables.TableClient;
import com.azure.data.tables.TableClientBuilder;
import com.azure.data.tables.models.TableEntity;
import com.azure.data.tables.models.TableServiceException;
import com.azure.data.tables.models.TableTransactionAction;
import com.azure.data.tables.models.TableTransactionActionType;
import com.predic8.membrane.core.config.security.acme.AzureTableStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;

public class AcmeAzureTableApiStorageEngine implements AcmeSynchronizedStorageEngine {

    private static final Logger log = LoggerFactory.getLogger(AcmeAzureTableApiStorageEngine.class);

    private final TableClient tableClient;
    private static final String TABLE_NAME = "membrane";
    private static final String PARTITION_NAME = "acme";
    private static final String CURRENT = "current";
    private static final String CURRENT_ERROR = "current-error";
    private static final String CURRENT_KEY = "current-key";


    public AcmeAzureTableApiStorageEngine(AzureTableStorage tableClient) {
        this.tableClient = new TableClientBuilder()
                .connectionString(tableClient.getConnectionString())
                .tableName(TABLE_NAME)
                .buildClient();

        try {
            this.tableClient.createTable();
        } catch (TableServiceException ignore) {
            log.debug("Ignore table already exists exception");
            // ignore if table exists already
        }

        log.debug("Loaded {}", this.getClass().getSimpleName());
    }

    private TableEntity getEntity(String rowKey) {
        try {
            log.debug("Get entity for {}", rowKey);
            return tableClient.getEntity(PARTITION_NAME, rowKey);
        } catch (TableServiceException e) {
            if (e.getResponse().getStatusCode() == 404) {
                log.debug("Entity {} does not exist, returning null", rowKey);
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

    private void upsertDataEntity(String rowKey, String data) {
        log.debug("Upserting key {}", rowKey);
        var entity = getEntity(rowKey);

        if (entity != null) {
            log.debug("Updating entity {}", rowKey);
            tableClient.updateEntity(new TableEntity(PARTITION_NAME, rowKey)
                    .addProperty("data", data)
            );
            return;
        }

        log.debug("Creating entity {}", rowKey);
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
        var first = getEntity(f1);

        if (first != null) {
            var updatedEntity = new TableEntity(PARTITION_NAME, f2)
                    .addProperty("data", first.getProperty("data"));

            var transactions = new ArrayList<TableTransactionAction>();
            transactions.add(new TableTransactionAction(TableTransactionActionType.CREATE, updatedEntity));
            transactions.add(new TableTransactionAction(TableTransactionActionType.DELETE, first));

            tableClient.submitTransaction(transactions);
            log.debug("Sent rename transaction, creating {} and removing {}", updatedEntity.getRowKey(), first.getRowKey());
            return;
        }

        log.debug("Attempt rename, but there was nothing to rename");
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
