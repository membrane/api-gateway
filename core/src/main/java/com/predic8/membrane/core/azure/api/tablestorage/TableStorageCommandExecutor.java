package com.predic8.membrane.core.azure.api.tablestorage;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

public class TableStorageCommandExecutor {

    private final TableStorageApi api;
    private final String path;

    public TableStorageCommandExecutor(TableStorageApi api) {
        this.api = api;

        path = api.config().getCustomHost() == null
                ? String.format("https://%s.table.core.windows.net/Tables", api.config().getStorageAccountName())
                : api.config().getCustomHost() + "/Tables";
    }

    public void create() throws Exception {
        var payload = Map.of(
                "TableName", api.config().getTableName()
        );

        var exc = api.http().call(
                api.requestBuilder(null)
                        .post(path)
                        .body(new ObjectMapper().writeValueAsString(payload))
                        .buildExchange()
        );

        var response = exc.getResponse();

        var isCreated = response.getStatusCode() == 201;
        var isExisting = response.getStatusCode() == 409;

        if (!(isCreated || isExisting)) {
            throw new RuntimeException(exc.getResponse().toString());
        }
    }
}
