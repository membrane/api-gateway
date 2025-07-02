/* Copyright 2023 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.azure;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.config.security.acme.AcmeValidation;

/**
 * @description Configures Azure DNS for ACME DNS-01 validation.
 * @topic 8. ACME
 */
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

    /**
     * @description The name of the Azure DNS zone.
     */
    @MCAttribute
    public void setDnsZoneName(String dnsZoneName) {
        this.dnsZoneName = dnsZoneName;
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }

    /**
     * @description The Azure subscription ID.
     */
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

    /**
     * @description The Azure tenant ID.
     */
    @MCAttribute
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getResourceGroup() {
        return resourceGroup;
    }

    /**
     * @description The Azure resource group.
     */
    @MCAttribute
    public void setResourceGroup(String resourceGroup) {
        this.resourceGroup = resourceGroup;
    }

    public String getResource() {
        return resource;
    }

    /**
     * @description The Azure resource.
     * @default https://management.azure.com
     */
    @MCAttribute
    public void setResource(String resource) {
        this.resource = resource;
    }

    public AzureIdentity getIdentity() {
        return identity;
    }

    /**
     * @description The Azure identity to use for authentication. It is used while registering ACME challenge responses
     * in Azure DNS.
     */
    @MCAttribute
    public void setIdentity(AzureIdentity identity) {
        this.identity = identity;
    }
}
