package com.predic8.membrane.core.azure.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.NoSuchElementException;

public class TableEntityCommandExecutor {

    private final AzureApiClient apiClient;
    private final String path;

    public TableEntityCommandExecutor(AzureApiClient apiClient, String rowKey) {
        this.apiClient = apiClient;
        path = apiClient.config().apiTableStorageBasePath()
                + "/" + apiClient.config().getTableName()
                + URLEncoder.encode("(PartitionKey='" + apiClient.config().getPartitionKey() + "',RowKey='" + rowKey + "')", StandardCharsets.UTF_8);
    }

    public JsonNode get() throws Exception {
        var res = apiClient.httpClient().call(
                apiClient.storageAccountRequestBuilder(path)
                        .get(path)
                        .buildExchange()
        ).getResponse();

        var response = new ObjectMapper().readTree(res.getBodyAsStringDecoded());

        if (response.has("odata.error")) {
            throw new NoSuchElementException(res.getBodyAsStringDecoded());
        }

        return response;
    }

    public void insertOrReplace(String data) throws Exception {
        var payload = Map.of("data", data);

        apiClient.httpClient().call(
                apiClient.storageAccountRequestBuilder(path)
                        .put(path)
                        .body(new ObjectMapper().writeValueAsString(payload))
                        .buildExchange()
        );
    }

    public void delete() throws Exception {
        apiClient.httpClient().call(
                apiClient.storageAccountRequestBuilder(path)
                        .delete(path)
                        .header("If-Match", "*")
                        .buildExchange()
        );
    }
}
