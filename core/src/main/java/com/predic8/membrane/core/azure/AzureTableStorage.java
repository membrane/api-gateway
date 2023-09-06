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
