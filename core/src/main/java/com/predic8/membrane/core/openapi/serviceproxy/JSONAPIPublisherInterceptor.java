/*
 *  Copyright 2023 predic8 GmbH, www.predic8.com
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.openapi.serviceproxy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.http.Response.ok;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static com.predic8.membrane.core.util.URLUtil.getHost;
import static java.time.LocalDateTime.now;

public class JSONAPIPublisherInterceptor extends AbstractInterceptor {
    public static final String PATH = "/apis.json";
    protected final Map<String, OpenAPIRecord> apis;
    private final ObjectMapper om = new ObjectMapper();
    private final ObjectWriter ow = new ObjectMapper().writerWithDefaultPrettyPrinter();

    public JSONAPIPublisherInterceptor(Map<String, OpenAPIRecord> apis) {
        this.apis = apis;
        name = "JSON:API Publisher";
    }

    @Override
    public String getShortDescription() {
        return "Publishes the JSON:API description.";
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        if (!exc.getRequest().getUri().startsWith(PATH)) return CONTINUE;

        return returnJsonOverview(exc);
    }

    private Outcome returnJsonOverview(Exchange exc) throws JsonProcessingException {
        exc.setResponse(ok().contentType(APPLICATION_JSON).body(ow.writeValueAsBytes(createJsonAPIStructure(exc.getOriginalHostHeader()))).build());

        return RETURN;
    }

    private void addAttributes(ObjectNode node) {
        addAttributeTimestamps(node.putObject("timestamps"));
        node.put("title", "Membrane API List");
    }

    private void addAttributeTimestamps(ObjectNode node) {
        node.put("created", now().toString());
        node.put("modified", now().toString());
    }

    private void addData(ArrayNode node, String hostHeader) {
        apis.forEach((k, v) -> addDataAPI(hostHeader, v, node.addObject()));
    }

    private void addDataAPI(String hostHeader, OpenAPIRecord value, ObjectNode node) {
        addDataAPIAttributes(hostHeader, value, node.putObject("attributes"));
        node.put("type", "apis");
    }

    private void addDataAPIAttributes(String hostHeader, OpenAPIRecord value, ObjectNode node) {
        JsonNode infoNode = value.node.get("info");

        addDataAPIAttributesBaseURL(node, value.node.get("servers").get(0).get("url").asText(), hostHeader);
        addDataAPIAttributesPaths(node.putArray("paths"), value);

        node.put("title", infoNode.get("title").asText());
        node.put("version", infoNode.get("version").asText());
    }

    private void addDataAPIAttributesBaseURL(ObjectNode node, String server, String hostHeader) {
        node.put("baseURL", server.replace(getHost(server), hostHeader));
    }

    private void addDataAPIAttributesPaths(ArrayNode node, OpenAPIRecord value) {
        value.node.get("paths").properties().forEach(it -> addDataAPIAttributesPathsContent(node, it.getKey(), it.getValue().properties()));
    }

    private void addDataAPIAttributesPathsContent(ArrayNode node, String key, Set<Entry<String, JsonNode>> entrySet) {
        entrySet.stream()
                .filter(it -> it.getValue().has("operationId"))
                .forEach(it -> addDataAPIAttributesPathsContentHelper(node.addObject(), key, it.getKey().toUpperCase()));
    }

    private void addDataAPIAttributesPathsContentHelper(ObjectNode node, String url, String type) {
        node.put("type", type).put("url", url);
    }

    private void addLinks(ObjectNode node, String hostHeader) {
        node.put("self", "https://%s%s".formatted(hostHeader, PATH));
    }

    private ObjectNode createJsonAPIStructure(String hostHeader) {
        ObjectNode node = om.createObjectNode();

        addAttributes(node.putObject("attributes"));
        addData(node.putArray("data"), hostHeader);
        addLinks(node.putObject("links"), hostHeader);
        node.put("version", 1.1); // JSON:API Version

        return node;
    }
}
