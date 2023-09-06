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
package com.predic8.membrane.core.azure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

public class AzureDnsApiSimulator {

    private static final Logger log = LoggerFactory.getLogger(AzureDnsApiSimulator.class);

    private final int port;
    private HttpRouter router;

    private Map<String, List<Map<String, String>>> tableStorage = new HashMap<>();

    public AzureDnsApiSimulator(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        router = new HttpRouter();
        router.setHotDeploy(false);

        var sp = new ServiceProxy(new ServiceProxyKey(port), "localhost", port);

        sp.getInterceptors().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) throws Exception {
                log.info("got request {}" + exc.getRequestURI());

                if (missingHeaders(exc)) {
                    exc.setResponse(Response.badRequest().build());
                    return Outcome.RETURN;
                }

                if (exc.getRequestURI().equals("/Tables")) {
                    return createTableStorageTable(exc);
                }

                if (exc.getRequestURI().startsWith("/membrane")) {
                    return switch (exc.getRequest().getMethod()) {
                        case "PUT" -> insertOrReplaceTableStorageEntity(exc);
                        case "GET" -> getEntityFromTableStorage(exc);
                        case "DELETE" -> deleteEntityFromTableStorage(exc);
                        default -> Outcome.RETURN;
                    };
                }

                exc.setResponse(Response.notFound().build());
                return Outcome.RETURN;
            }
        });

        router.add(sp);
        router.start();
    }

    private boolean missingHeaders(Exchange exc) {
        var hasNeededHeaders = Arrays.stream(exc.getRequest().getHeader().getAllHeaderFields()).allMatch(headerField ->
                List.of("Date", "x-ms-version", "DataServiceVersion", "MaxDataServiceVersion", "Authorization")
                        .contains(headerField.getHeaderName())
        );

        if (!hasNeededHeaders) {
            return false;
        }

        return true;
    }

    private Outcome deleteEntityFromTableStorage(Exchange exc) {
        var uriPayload = extractValuesFromUri(exc.getRequestURI());

        if (uriPayload == null) {
            exc.setResponse(Response.badRequest().build());
            return Outcome.RETURN;
        }

        var tableName = uriPayload.get("tableName");
        var rowKey = uriPayload.get("rowKey");

        tableStorage.put(tableName,
                tableStorage.get(tableName).stream()
                        .filter(map -> !map.get("RowKey").equals(rowKey))
                        .toList()
        );

        exc.setResponse(Response.statusCode(204).build());
        return Outcome.RETURN;
    }

    private Outcome insertOrReplaceTableStorageEntity(Exchange exc) throws JsonProcessingException {
        var data = new ObjectMapper()
                .readTree(exc.getRequest().getBodyAsStringDecoded())
                .get("data")
                .asText();

        if (data == null) {
            exc.setResponse(Response.badRequest().build());
            return Outcome.RETURN;
        }

        var uriPayload = extractValuesFromUri(exc.getRequestURI());

        if (uriPayload == null) {
            exc.setResponse(Response.badRequest().build());
            return Outcome.RETURN;
        }

        var tableName = uriPayload.get("tableName");
        var partitionKey = uriPayload.get("partitionKey");
        var rowKey = uriPayload.get("rowKey");

        if (!tableStorage.containsKey(tableName)) {
            exc.setResponse(Response.notFound().build());
            return Outcome.RETURN;
        }

        var table = tableStorage.get(tableName);
        var row = table.stream().filter(t -> t.get("RowKey").equals(rowKey)).findFirst();

        if (row.isPresent()) {
            row.get().put("data", data);
        } else {
            table.add(new HashMap<>(Map.of(
                    "PartitionKey", partitionKey,
                    "RowKey", rowKey,
                    "data", data
            )));
        }

        exc.setResponse(Response.statusCode(204).build());
        return Outcome.RETURN;
    }

    private Outcome getEntityFromTableStorage(Exchange exc) throws JsonProcessingException {
        var uriPayload = extractValuesFromUri(exc.getRequestURI());

        if (uriPayload == null) {
            exc.setResponse(Response.badRequest().build());
            return Outcome.RETURN;
        }

        var tableName = uriPayload.get("tableName");
        var rowKey = uriPayload.get("rowKey");

        var row = tableStorage.get(tableName).stream()
                .filter(e -> e.get("RowKey").equals(rowKey))
                .findFirst();

        if (row.isEmpty()) {
            exc.setResponse(Response.statusCode(400)
                    .body(new ObjectMapper().writeValueAsString(Map.of("odata.error", "foo")))
                    .build());
            return Outcome.RETURN;
        }

        exc.setResponse(Response.statusCode(200)
                .body(new ObjectMapper().writeValueAsString(row.get()))
                .build());
        return Outcome.RETURN;
    }

    private Map<String, String> extractValuesFromUri(String uri) {
        var pattern = Pattern.compile("/(\\w+)%28PartitionKey%3D%27(\\w+)%27%2CRowKey%3D%27(\\w+)%27%29");
        var matcher = pattern.matcher(uri);

        if (matcher.matches()) {
            return Map.of(
                    "tableName", matcher.group(1),
                    "partitionKey", matcher.group(2),
                    "rowKey", matcher.group(3)
            );
        }

        return null;
    }

    private Outcome createTableStorageTable(Exchange exc) throws JsonProcessingException {
        var tableName = new ObjectMapper()
                .readTree(exc.getRequest().getBodyAsStringDecoded())
                .get("TableName")
                .asText();

        if (tableName == null || tableName.isBlank() || tableName.isEmpty()) {
            exc.setResponse(Response.badRequest().build());
            return Outcome.RETURN;
        }

        if (tableStorage.containsKey(tableName)) {
            exc.setResponse(Response.statusCode(409).build());
        } else {
            tableStorage.put(tableName, new ArrayList<>());
            exc.setResponse(Response.statusCode(201).build());
        }

        return Outcome.RETURN;
    }

    public void stop() {
        router.stop();
    }
}
