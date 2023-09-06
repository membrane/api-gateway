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
