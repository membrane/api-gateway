/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.discovery;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;

import java.util.*;

import static com.predic8.membrane.core.http.MimeType.*;

/**
 * Renders a {@link Catalog} in Membrane's own format: everything the gateway knows about an API,
 * including the method and path of each of its operations, which neither APIs.json nor RFC 9727
 * carries. Use it to drive a portal or an inventory that would otherwise have to fetch and parse
 * every OpenAPI document separately.
 * <p>
 * Members that carry nothing are left out, so the entry of a plain <code>serviceProxy</code> stays
 * short.
 */
public class MembraneCatalogRenderer implements CatalogRenderer {

    private static final ObjectMapper om = new ObjectMapper();
    private static final ObjectWriter ow = new ObjectMapper().writerWithDefaultPrettyPrinter();

    @Override
    public String contentType() {
        return APPLICATION_JSON;
    }

    @Override
    public boolean rendersOperations() {
        return true;
    }

    @Override
    public byte[] render(Catalog catalog) throws JsonProcessingException {
        ObjectNode document = om.createObjectNode();
        document.put("aid", catalog.aid());
        document.put("name", catalog.name());
        document.put("description", catalog.description());
        putIfPresent(document, "url", catalog.url());
        document.put("created", catalog.created().toString());
        document.put("modified", catalog.modified().toString());
        ArrayNode apis = document.putArray("apis");
        catalog.apis().forEach(entry -> apis.add(renderApi(entry)));
        return ow.writeValueAsBytes(document);
    }

    private ObjectNode renderApi(CatalogEntry entry) {
        ObjectNode api = om.createObjectNode();
        api.put("aid", entry.aid());
        api.put("kind", entry.kind().getLabel());
        api.put("name", entry.name());
        api.put("description", entry.description());
        putIfPresent(api, "version", entry.version());
        api.set("endpoint", renderEndpoint(entry.endpoint()));
        renderLinks(api, entry.links());
        putStrings(api, "tags", entry.tags());
        renderContact(api, entry.contact());
        renderLicense(api, entry.license());
        putIfPresent(api, "termsOfService", entry.termsOfService());
        renderSoap(api, entry.soap());
        renderOperations(api, entry.operations());
        return api;
    }

    private ObjectNode renderEndpoint(CatalogEntry.Endpoint endpoint) {
        ObjectNode node = om.createObjectNode();
        node.put("protocol", endpoint.protocol());
        node.put("host", endpoint.host());
        node.put("port", endpoint.port());
        node.put("path", endpoint.path());
        putIfPresent(node, "method", endpoint.method());
        node.put("url", endpoint.url());
        return node;
    }

    private void renderLinks(ObjectNode api, CatalogEntry.Links links) {
        if (links.humanUrl() == null && links.specUrl() == null)
            return;
        ObjectNode node = api.putObject("links");
        putIfPresent(node, "humanUrl", links.humanUrl());
        putIfPresent(node, "specUrl", links.specUrl());
    }

    private void renderContact(ObjectNode api, CatalogEntry.Contact contact) {
        if (contact == null)
            return;
        ObjectNode node = om.createObjectNode();
        putIfPresent(node, "name", contact.name());
        putIfPresent(node, "email", contact.email());
        putIfPresent(node, "url", contact.url());
        if (!node.isEmpty())
            api.set("contact", node);
    }

    private void renderLicense(ObjectNode api, CatalogEntry.License license) {
        if (license == null)
            return;
        ObjectNode node = om.createObjectNode();
        putIfPresent(node, "name", license.name());
        putIfPresent(node, "url", license.url());
        if (!node.isEmpty())
            api.set("license", node);
    }

    private void renderSoap(ObjectNode api, CatalogEntry.SoapInfo soap) {
        if (soap == null)
            return;
        ObjectNode node = om.createObjectNode();
        putIfPresent(node, "wsdl", soap.wsdl());
        putIfPresent(node, "serviceName", soap.serviceName());
        putIfPresent(node, "portName", soap.portName());
        if (!node.isEmpty())
            api.set("soap", node);
    }

    private void renderOperations(ObjectNode api, List<CatalogOperation> operations) {
        if (operations.isEmpty())
            return;
        ArrayNode node = api.putArray("operations");
        operations.forEach(operation -> {
            ObjectNode entry = om.createObjectNode();
            entry.put("method", operation.method());
            entry.put("path", operation.path());
            putIfPresent(entry, "operationId", operation.operationId());
            putIfPresent(entry, "summary", operation.summary());
            putStrings(entry, "tags", operation.tags());
            if (operation.deprecated())
                entry.put("deprecated", true);
            node.add(entry);
        });
    }

    private static void putStrings(ObjectNode node, String name, List<String> values) {
        if (values.isEmpty())
            return;
        ArrayNode array = node.putArray(name);
        values.forEach(array::add);
    }

    private static void putIfPresent(ObjectNode node, String name, String value) {
        if (value != null)
            node.put(name, value);
    }
}
