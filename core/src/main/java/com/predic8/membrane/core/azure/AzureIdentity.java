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
