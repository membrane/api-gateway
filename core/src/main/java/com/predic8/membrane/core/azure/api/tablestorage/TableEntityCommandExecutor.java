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

import tools.jackson.databind.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.transport.http.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.net.*;
import java.util.*;

import static java.net.URLEncoder.*;
import static java.nio.charset.StandardCharsets.*;

public class TableEntityCommandExecutor {

    private final TableStorageApi api;
    private final String path;
    private final String queryPath;

    public TableEntityCommandExecutor(TableStorageApi api, String rowKey) {
        this.api = api;
        path = createPath(api, rowKey);
        queryPath = createQueryPath(api);
    }

    private static @NotNull String createQueryPath(TableStorageApi api) {
        return api.config().getCustomHost() == null ? createTableAddressWithStorageAccount(api) : createAddressWithHost(api);
    }

    private static @NotNull String createAddressWithHost(TableStorageApi api) {
        return String.format("%s/%s", api.config().getCustomHost(), api.config().getTableName());
    }

    private static @NotNull String createTableAddressWithStorageAccount(TableStorageApi api) {
        return String.format("https://%s.table.core.windows.net/%s", api.config().getStorageAccountName(), api.config().getTableName());
    }

    private static @NotNull String createPath(TableStorageApi api, String rowKey) {
        return api.config().getCustomHost() == null ? createTableStorageUri(api, rowKey) : createTableStorageUri2(api, rowKey);
    }

    private static @NotNull String createTableStorageUri2(TableStorageApi api, String rowKey) {
        return String.format("%s/%s%s", api.config().getCustomHost(), api.config().getTableName(), buildUriBracketParams(api, rowKey));
    }

    private static @NotNull String createTableStorageUri(TableStorageApi api, String rowKey) {
        return String.format("https://%s.table.core.windows.net/%s%s", api.config().getStorageAccountName(), api.config().getTableName(), buildUriBracketParams(api, rowKey));
    }

    private static String buildUriBracketParams(TableStorageApi api, String rowKey) {
        return encode("(PartitionKey='%s'%s)".formatted(api.config().getPartitionKey(),rowKey(rowKey)), UTF_8);
    }

    private static String rowKey(String rowKey) {
        return rowKey != null ? ",RowKey='%s'".formatted( rowKey) : "";
    }

    public JsonNode get() throws Exception {
        try (HttpClient hc = api.http()) {
            var res =hc.call(api.requestBuilder(path).get(path).buildExchange()).getResponse();

            var response = new ObjectMapper().readTree(res.getBodyAsStringDecoded());

            if (response.has("odata.error")) {
                throw new NoSuchElementException(res.getBodyAsStringDecoded());
            }
            return response;
        }
    }

    public void insertOrReplace(String data) throws Exception {
        try (HttpClient hc = api.http()) {
            var exc = hc.call(
                    api.requestBuilder(path)
                            .put(path)
                            .body(new ObjectMapper().writeValueAsString(Map.of("data", data)))
                            .buildExchange()
            );
            if (exc.getResponse().getStatusCode() != 204) {
                throw new RuntimeException(exc.getResponse().toString());
            }
        }
    }

    public void delete() throws Exception {
        try (HttpClient hc = api.http()) {
            var exc = hc.call(
                    api.requestBuilder(path)
                            .delete(path)
                            .header("If-Match", "*")
                            .buildExchange()
            );
            if (exc.getResponse().getStatusCode() != 204) {
                throw new RuntimeException(exc.getResponse().toString());
            }
        }
    }

    public Iterator<JsonNode> query() {
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
            try(HttpClient hc = api.http()) {
                res = hc.call(exc).getResponse();
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
                throw new RuntimeException("API returned error: " + r);
            }

            if (!r.has("value")) {
                internalPageIterator = null;
                return;
            }
            internalPageIterator = r.get("value").iterator();
        }

        /**
         * @return whether the page has been flipped successfully
         */
        private boolean flipPage() {
            String npk = getXmsContinuationNextPartitionKey();
            String nrk = getXmsContinuationNextRowKey();

            if (npk == null || nrk == null)
                return false;

            try {
                exc = api.requestBuilder(queryPath)
                        .get(getPath(npk, nrk))
                        .buildExchange();
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }

            preparePageForIteration();
            return true;
        }

        private @NotNull String getPath(String npk, String nrk) {
            return queryPath + "?NextPartitionKey=" + npk + "&NextRowKey=" + nrk;
        }

        private String getXmsContinuationNextRowKey() {
            return exc.getResponse().getHeader().getFirstValue("x-ms-continuation-NextRowKey");
        }

        private String getXmsContinuationNextPartitionKey() {
            return exc.getResponse().getHeader().getFirstValue("x-ms-continuation-NextPartitionKey");
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