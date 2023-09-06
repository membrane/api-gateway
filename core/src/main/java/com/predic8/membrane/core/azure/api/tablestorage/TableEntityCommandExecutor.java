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

        path = api.config().getCustomHost() == null
                ? String.format("https://%s.table.core.windows.net/%s%s",
                    api.config().getStorageAccountName(),
                    api.config().getTableName(),
                    URLEncoder.encode("(PartitionKey='" + api.config().getPartitionKey() + "',RowKey='" + rowKey + "')", StandardCharsets.UTF_8))
                : String.format("%s/%s%s",
                    api.config().getCustomHost(),
                    api.config().getTableName(),
                    URLEncoder.encode("(PartitionKey='" + api.config().getPartitionKey() + "',RowKey='" + rowKey + "')", StandardCharsets.UTF_8));
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
