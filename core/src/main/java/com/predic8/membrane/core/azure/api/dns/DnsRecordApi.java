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
package com.predic8.membrane.core.azure.api.dns;

import com.predic8.membrane.core.azure.AzureDns;
import com.predic8.membrane.core.azure.api.AzureApiClient;
import com.predic8.membrane.core.azure.api.HttpClientConfigurable;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.transport.http.HttpClient;

public class DnsRecordApi implements HttpClientConfigurable<AzureDns> {

    private final AzureApiClient apiClient;
    private final AzureDns config;

    public DnsRecordApi(AzureApiClient apiClient, AzureDns config) {
        this.apiClient = apiClient;
        this.config = config;
    }

    public DnsRecordCommandExecutor txt(String name) {
        return new DnsRecordCommandExecutor(this, name, DnsRecordType.TXT);
    }

    protected Request.Builder requestBuilder() throws Exception {
        return new Request.Builder()
                .header("Authorization", "Bearer " + apiClient.auth().accessToken());
    }

    @Override
    public HttpClient http() {
        return apiClient.httpClient();
    }

    @Override
    public AzureDns config() {
        return config;
    }
}
