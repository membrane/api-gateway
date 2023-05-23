package com.predic8.membrane.core.config.security.acme;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;

@MCElement(name = "azureTableStorage", topLevel = false)
public class AzureTableStorage implements AcmeSynchronizedStorage {

    private String connectionString;

    public String getConnectionString() {
        return connectionString;
    }

    @MCAttribute
    public void setConnectionString(String connectionString) {
        this.connectionString = connectionString;
    }
}
