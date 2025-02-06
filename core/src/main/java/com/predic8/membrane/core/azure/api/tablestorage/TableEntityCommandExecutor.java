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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

import static java.net.URLEncoder.encode;
import static java.nio.charset.StandardCharsets.UTF_8;

public class TableEntityCommandExecutor {

    private final TableStorageApi api;
    private final String path;
    private final String queryPath;

    public TableEntityCommandExecutor(TableStorageApi api, String rowKey) {
        this.api = api;

        path = api.config().getCustomHost() == null
                ? String.format("https://%s.table.core.windows.net/%s%s",
                api.config().getStorageAccountName(),
                api.config().getTableName(),
                buildUriBracketParams(api, rowKey))
                : String.format("%s/%s%s",
                api.config().getCustomHost(),
                api.config().getTableName(),
                buildUriBracketParams(api, rowKey));
        queryPath = api.config().getCustomHost() == null
                ? String.format("https://%s.table.core.windows.net/%s",
                api.config().getStorageAccountName(),
                api.config().getTableName())
                : String.format("%s/%s",
                api.config().getCustomHost(),
                api.config().getTableName());
    }

    private static String buildUriBracketParams(TableStorageApi api, String rowKey) {
        String params = "(PartitionKey='" + api.config().getPartitionKey() + "'";
        if (rowKey != null) {
            params += ",RowKey='" + rowKey + "'";
        }
        params +=")";
        return encode(params, UTF_8);
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

    public Iterator<JsonNode> query() throws Exception {
        return new TableEntityIterator();
    }

    class TableEntityIterator implements Iterator<JsonNode> {
        private Iterator<JsonNode> internalPageIterator;
        private Exchange exc;

        TableEntityIterator() {
            try {
                exc = api.requestBuilder(queryPath + encode("()", UTF_8))
                        .get(queryPath + encode("()", UTF_8))
                        .buildExchange();
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }

            preparePageForIteration();
        }

        private void preparePageForIteration() {
            Response res;
            try {
                res = api.http().call(exc).getResponse();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            if (res.getStatusCode() != 200)
                throw new RuntimeException("API returned status code != 200:" + res);

            JsonNode r;
            try {
                r = new ObjectMapper().readTree(res.getBodyAsStreamDecoded());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            if (r.has("odata.error")) {
                throw new RuntimeException("API returned error: " + r.toString());
            }

            if (!r.has("value")) {
                internalPageIterator = null;
                return;
            }

            ArrayNode an = (ArrayNode) r.get("value");
            internalPageIterator = an.iterator();
        }

        /**
         * @return whether the page has been flipped successfully
         */
        private boolean flipPage() {
            String npk = exc.getResponse().getHeader().getFirstValue("x-ms-continuation-NextPartitionKey");
            String nrk = exc.getResponse().getHeader().getFirstValue("x-ms-continuation-NextRowKey");

            if (npk == null || nrk == null)
                return false;

            String path = queryPath + "?NextPartitionKey=" + npk + "&NextRowKey=" + nrk;
            try {
                exc = api.requestBuilder(queryPath)
                        .get(path)
                        .buildExchange();
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }

            preparePageForIteration();
            return true;
        }

        @Override
        public boolean hasNext() {
            if (internalPageIterator == null)
                return false;
            if (internalPageIterator.hasNext())
                return true;
            if (!flipPage())
                return false;
            return hasNext();
        }

        @Override
        public JsonNode next() {
            JsonNode result = internalPageIterator.next();
            if (result != null)
                return result;
            if (!flipPage())
                return null;
            return internalPageIterator.next();
        }
    }
}
