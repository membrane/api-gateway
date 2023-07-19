package com.predic8.membrane.core.azure;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.config.security.acme.AcmeSynchronizedStorage;
import com.predic8.membrane.core.transport.http.client.HttpClientConfiguration;

@MCElement(name = "azureTableStorage", topLevel = false)
public class AzureTableStorage implements AcmeSynchronizedStorage {

    private String storageAccountName;
    private String storageAccountKey;
    private String tableName = "membrane";
    private String partitionKey = "acme";
    private HttpClientConfiguration httpClientConfiguration;

    private String customHost;

    public String getCustomHost() {
        return customHost;
    }

    public void setCustomHost(String customHost) {
        this.customHost = customHost;
    }

    public String getStorageAccountName() {
        return storageAccountName;
    }

    @MCAttribute
    public void setStorageAccountName(String storageAccountName) {
        this.storageAccountName = storageAccountName;
    }

    public String getStorageAccountKey() {
        return storageAccountKey;
    }

    @MCAttribute
    public void setStorageAccountKey(String storageAccountKey) {
        this.storageAccountKey = storageAccountKey;
    }

    public String getTableName() {
        return tableName;
    }

    @MCAttribute
    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getPartitionKey() {
        return partitionKey;
    }

    @MCAttribute
    public void setPartitionKey(String partitionKey) {
        this.partitionKey = partitionKey;
    }

    public HttpClientConfiguration getHttpClientConfiguration() {
        return httpClientConfiguration;
    }

    @MCChildElement
    public void setHttpClientConfiguration(HttpClientConfiguration httpClientConfiguration) {
        this.httpClientConfiguration = httpClientConfiguration;
    }
}
