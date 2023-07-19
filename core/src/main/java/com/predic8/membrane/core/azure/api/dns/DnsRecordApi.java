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
