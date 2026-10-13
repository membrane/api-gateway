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

import static com.predic8.membrane.core.http.MimeType.*;

/**
 * Renders a {@link Catalog} as an APIs.json document, specification version 0.18.
 * <p>
 * Note the capitalisation of <code>humanURL</code> and <code>baseURL</code>. The specification is
 * inconsistent here: its field list spells them <code>humanUrl</code> and <code>baseUrl</code>, its
 * examples, and the tooling built on them, <code>humanURL</code> and <code>baseURL</code>. This
 * renderer follows the examples.
 * The <code>properties</code> array is what makes the document machine readable, so an API
 * described by an OpenAPI document gets an <code>OpenAPI</code> entry pointing at the document
 * Membrane publishes and a <code>Documentation</code> entry pointing at its Swagger UI, and a SOAP
 * proxy gets a <code>WSDL</code> entry. Terms of service and license from the OpenAPI document
 * become <code>TermsOfService</code> and <code>InterfaceLicense</code> entries.
 *
 * @see <a href="https://apisjson.org/">apisjson.org</a>
 */
public class ApisJsonRenderer implements CatalogRenderer {

    static final String SPECIFICATION_VERSION = "0.18";

    private static final ObjectMapper om = new ObjectMapper();

    @Override
    public String contentType() {
        return APPLICATION_JSON;
    }

    @Override
    public byte[] render(Catalog catalog) throws JsonProcessingException {
        ObjectNode collection = om.createObjectNode();
        collection.put("aid", catalog.aid());
        collection.put("name", catalog.name());
        collection.put("description", catalog.description());
        putIfPresent(collection, "url", catalog.url());
        collection.put("created", catalog.created().toString());
        collection.put("modified", catalog.modified().toString());
        collection.put("specificationVersion", SPECIFICATION_VERSION);
        ArrayNode apis = collection.putArray("apis");
        catalog.apis().forEach(entry -> apis.add(renderApi(entry)));
        return om.writeValueAsBytes(collection);
    }

    private ObjectNode renderApi(CatalogEntry entry) {
        ObjectNode api = om.createObjectNode();
        api.put("aid", entry.aid());
        api.put("name", entry.name());
        api.put("description", entry.description());
        putIfPresent(api, "humanURL", entry.links().humanUrl());
        putIfPresent(api, "baseURL", entry.endpoint().url());
        putIfPresent(api, "version", entry.version());
        renderTags(api, entry);
        renderProperties(api, entry);
        return api;
    }

    private void renderTags(ObjectNode api, CatalogEntry entry) {
        if (entry.tags().isEmpty())
            return;
        ArrayNode tags = api.putArray("tags");
        entry.tags().forEach(tags::add);
    }

    /**
     * Only APIs with something to link get a <code>properties</code> array. An empty array would
     * be noise: the whole point of the member is to carry machine readable links.
     */
    private void renderProperties(ObjectNode api, CatalogEntry entry) {
        final var properties = om.createArrayNode();
        if (entry.links().specUrl() != null) {
            properties.add(renderProperty("OpenAPI", null, entry.links().specUrl()));
            if (entry.links().humanUrl() != null)
                properties.add(renderProperty("Documentation", null, entry.links().humanUrl()));
        }
        if (entry.soap() != null && entry.soap().wsdl() != null)
            properties.add(renderProperty("WSDL", null, entry.soap().wsdl()));
        if (entry.termsOfService() != null)
            properties.add(renderProperty("TermsOfService", null, entry.termsOfService()));
        if (entry.license() != null && entry.license().url() != null)
            properties.add(renderProperty("InterfaceLicense", entry.license().name(), entry.license().url()));
        if (!properties.isEmpty())
            api.set("properties", properties);
    }

    private ObjectNode renderProperty(String type, String name, String url) {
        ObjectNode property = om.createObjectNode();
        property.put("type", type);
        putIfPresent(property, "name", name);
        property.put("url", url);
        return property;
    }

    private static void putIfPresent(ObjectNode node, String name, String value) {
        if (value != null)
            node.put(name, value);
    }
}
