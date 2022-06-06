package com.predic8.membrane.core.config.security.acme;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import org.springframework.beans.factory.annotation.Required;

@MCElement(name = "kubernetesStorage", topLevel = false)
public class KubernetesStorage implements AcmeSynchronizedStorage {
    String namespace;
    String masterLease;
    String accountSecret;
    String prefix;

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
}
