/* Copyright 2022 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.config.security.acme;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.Required;

import java.util.Objects;

@MCElement(name = "kubernetesStorage", topLevel = false)
public class KubernetesStorage implements AcmeSynchronizedStorage {
    String baseURL;
    String namespace;
    String masterLease;
    String accountSecret;
    String prefix;

    public String getBaseURL() {
        return baseURL;
    }

    @MCAttribute
    public void setBaseURL(String baseURL) {
        this.baseURL = baseURL;
    }

    public String getNamespace() {
        return namespace;
    }

    @MCAttribute
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getMasterLease() {
        return masterLease;
    }

    @Required
    @MCAttribute
    public void setMasterLease(String masterLease) {
        this.masterLease = masterLease;
    }

    public String getAccountSecret() {
        return accountSecret;
    }

    @MCAttribute
    public void setAccountSecret(String accountSecret) {
        this.accountSecret = accountSecret;
    }

    public String getPrefix() {
        return prefix;
    }

    /**
     * Prefix to use for naming Kubernetes Secrets and ConfigMaps.
     */
    @MCAttribute
    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        KubernetesStorage that = (KubernetesStorage) o;
        return Objects.equals(baseURL, that.baseURL) && Objects.equals(namespace, that.namespace) && Objects.equals(masterLease, that.masterLease) && Objects.equals(accountSecret, that.accountSecret) && Objects.equals(prefix, that.prefix);
    }

    @Override
    public int hashCode() {
        return Objects.hash(baseURL, namespace, masterLease, accountSecret, prefix);
    }
}
