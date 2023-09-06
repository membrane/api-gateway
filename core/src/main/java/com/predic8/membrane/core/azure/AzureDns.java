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
