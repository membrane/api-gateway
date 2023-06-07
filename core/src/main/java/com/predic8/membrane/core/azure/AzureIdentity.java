package com.predic8.membrane.core.azure;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;

@MCElement(name = "azureIdentity")
public class AzureIdentity {

    private String grantType = "client_credentials";
    private String clientId;
    private String clientSecret;
    private String resource = "https://management.azure.com";
    private String tenantId;

    public String getGrantType() {
        return grantType;
    }

    @MCAttribute
    public void setGrantType(String grantType) {
        this.grantType = grantType;
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

    public String getTenantId() {
        return tenantId;
    }

    @MCAttribute
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }
}
