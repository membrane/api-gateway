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
package com.predic8.membrane.core.azure.api;

import com.predic8.membrane.core.azure.AzureDns;
import com.predic8.membrane.core.azure.AzureIdentity;
import com.predic8.membrane.core.azure.AzureTableStorage;
import com.predic8.membrane.core.azure.api.auth.AuthenticationApi;
import com.predic8.membrane.core.azure.api.dns.DnsRecordApi;
import com.predic8.membrane.core.azure.api.tablestorage.TableStorageApi;
import com.predic8.membrane.core.transport.http.HttpClient;
import com.predic8.membrane.core.transport.http.HttpClientFactory;
import com.predic8.membrane.core.util.TimerManager;

import javax.annotation.Nullable;

public class AzureApiClient implements AutoCloseable {

    private final HttpClient httpClient;
    private final AuthenticationApi authApi;
    private final TableStorageApi tableStorageApi;


    public AzureApiClient(
            @Nullable AzureIdentity identityConfig,
            AzureTableStorage tableStorage,
            HttpClientFactory httpClientFactory
    ) {
        if (httpClientFactory == null) {
            httpClientFactory = new HttpClientFactory(new TimerManager());
        }
        this.httpClient = httpClientFactory.createClient(tableStorage.getHttpClientConfiguration());

        authApi = new AuthenticationApi(httpClient, identityConfig);
        tableStorageApi = new TableStorageApi(this, tableStorage);
    }

    public DnsRecordApi dnsRecords(AzureDns dnsOperator) {
        return new DnsRecordApi(this, dnsOperator);
    }

    public TableStorageApi tableStorage() {
        return tableStorageApi;
    }

    public AuthenticationApi auth() {
        return authApi;
    }

    public HttpClient httpClient() {
        return httpClient;
    }

    @Override
    public void close() throws Exception {
        this.httpClient.close();
    }
}
