package com.predic8.membrane.core.azure.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

public class TableStorageCommandExecutor {
    private final AzureApiClient apiClient;
    private final String path;

    public TableStorageCommandExecutor(AzureApiClient apiClient) {
        this.apiClient = apiClient;

        path = apiClient.config().apiTableStorageBasePath() + "/Tables";
    }

    public void create() throws Exception {
        var payload = Map.of(
                "TableName", apiClient.config().getTableName()
        );

        apiClient.httpClient().call(
                apiClient.storageAccountRequestBuilder(null)
                        .post(path)
                        .body(new ObjectMapper().writeValueAsString(payload))
                        .buildExchange()
        );
    }
}
