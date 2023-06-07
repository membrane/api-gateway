package com.predic8.membrane.core.azure.api.tablestorage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.NoSuchElementException;

public class TableEntityCommandExecutor {

    private final TableStorageApi api;
    private final String path;

    public TableEntityCommandExecutor(TableStorageApi api, String rowKey) {
        this.api = api;

        path = String.format("https://%s.table.core.windows.net/%s%s",
                api.config().getStorageAccountName(),
                api.config().getTableName(),
                URLEncoder.encode("(PartitionKey='" + api.config().getPartitionKey() + "',RowKey='" + rowKey + "')", StandardCharsets.UTF_8)
        );
    }

    public JsonNode get() throws Exception {
        var res = api.http().call(
                api.requestBuilder(path)
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

        var exc = api.http().call(
                api.requestBuilder(path)
                        .put(path)
                        .body(new ObjectMapper().writeValueAsString(payload))
                        .buildExchange()
        );

        if (exc.getResponse().getStatusCode() != 204) {
            throw new RuntimeException(exc.getResponse().toString());
        }
    }

    public void delete() throws Exception {
        var exc = api.http().call(
                api.requestBuilder(path)
                        .delete(path)
                        .header("If-Match", "*")
                        .buildExchange()
        );

        var response = exc.getResponse();

        if (response.getStatusCode() != 204) {
            throw new RuntimeException(response.toString());
        }
    }
}
