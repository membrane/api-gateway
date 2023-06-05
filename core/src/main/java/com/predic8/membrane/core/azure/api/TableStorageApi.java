package com.predic8.membrane.core.azure.api;

public class TableStorageApi {

    private final AzureApiClient apiClient;

    public TableStorageApi(AzureApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public TableStorageCommandExecutor table() {
        return new TableStorageCommandExecutor(apiClient);
    }

    public TableEntityCommandExecutor entity(String rowKey) {
        return new TableEntityCommandExecutor(apiClient, rowKey);
    }
}
