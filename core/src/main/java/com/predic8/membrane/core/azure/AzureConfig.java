package com.predic8.membrane.core.azure;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.config.security.acme.AcmeSynchronizedStorage;

@MCElement(name = "azureConfig", topLevel = false)
public class AzureConfig implements AcmeSynchronizedStorage {
    private String grantType = "client_credentials";
    private String tenantId;
    private String clientId;
    private String clientSecret;
    private String resource = "https://management.azure.com";
    private String subscriptionId;
    private String resourceGroup;
    private String domainName;
    private String storageAccountName;
    private String storageAccountKey;
    private String tableName = "membrane";
    private String partitionKey = "acme";

    public String apiTableStorageBasePath() {
        return "https://" + getStorageAccountName() + ".table.core.windows.net";
    }

    public String apiBasePath() {
        return apiSubscriptionBasePath() + "/providers";
    }

    public String apiResourceBasePath() {
        return apiSubscriptionBasePath() + "/resourceGroups/" + getResourceGroup() + "/providers";
    }

    public String apiSubscriptionBasePath() {
        return resource + "/subscriptions/" + getSubscriptionId();
    }

    public String getGrantType() {
        return grantType;
    }

    @MCAttribute
    public void setGrantType(String grantType) {
        this.grantType = grantType;
    }

    public String getTenantId() {
        return tenantId;
    }

    @MCAttribute
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getClientId() {
        return clientId;
    }

    @MCAttribute
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    @MCAttribute
    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getResource() {
        return resource;
    }

    @MCAttribute
    public void setResource(String resource) {
        this.resource = resource;
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }

    @MCAttribute
    public void setSubscriptionId(String subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    public String getResourceGroup() {
        return resourceGroup;
    }

    @MCAttribute
    public void setResourceGroup(String resourceGroup) {
        this.resourceGroup = resourceGroup;
    }

    public String getDomainName() {
        return domainName;
    }

    @MCAttribute
    public void setDomainName(String domainName) {
        this.domainName = domainName;
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

    public String getPartitionKey() {
        return partitionKey;
    }
}
