package com.predic8.membrane.core.azure.api;

import com.predic8.membrane.core.azure.api.records.DnsRecordType;

public class DnsRecordApi {

    private final AzureApiClient apiClient;

    public DnsRecordApi(AzureApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public DnsRecordCommandExecutor txt(String name) {
        return new DnsRecordCommandExecutor(apiClient, name, DnsRecordType.TXT);
    }
}
