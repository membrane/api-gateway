package com.predic8.membrane.core.azure;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.config.security.acme.AcmeValidation;

@MCElement(topLevel = false, name = "azureDns")
public class AzureDns extends AcmeValidation {

    private String dnsZoneName;
    private String subscriptionId;
    private String tenantId;
    private String resourceGroup;
    private String resource = "https://management.azure.com";
    private AzureIdentity identity;

    public String getDnsZoneName() {
        return dnsZoneName;
    }

    @MCAttribute
    public void setDnsZoneName(String dnsZoneName) {
        this.dnsZoneName = dnsZoneName;
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }

    @MCAttribute
    public void setSubscriptionId(String subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    public String getTenantId() {
        if (identity != null) {
            return identity.getTenantId();
        }
        return tenantId;
    }

    @MCAttribute
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getResourceGroup() {
        return resourceGroup;
    }

    @MCAttribute
    public void setResourceGroup(String resourceGroup) {
        this.resourceGroup = resourceGroup;
    }

    public String getResource() {
        return resource;
    }

    @MCAttribute
    public void setResource(String resource) {
        this.resource = resource;
    }

    public AzureIdentity getIdentity() {
        return identity;
    }

    @MCAttribute
    public void setIdentity(AzureIdentity identity) {
        this.identity = identity;
    }
}
