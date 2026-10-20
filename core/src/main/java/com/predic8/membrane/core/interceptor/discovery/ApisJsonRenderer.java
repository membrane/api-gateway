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

import java.time.*;
import java.util.*;

import static com.predic8.membrane.core.http.MimeType.*;

/**
 * Renders a {@link Catalog} as an APIs.json document, specification version 0.24.
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
 * <p>
 * The JSON Schema of APIs.json makes more members mandatory than the specification text, among
 * them <code>image</code>, <code>maintainers</code> and <code>tags</code>. The text is the source
 * of truth, so the renderer follows it and leaves out what it has no value for.
 *
 * @see <a href="https://apisjson.org/">apisjson.org</a>
 */
public class ApisJsonRenderer implements CatalogRenderer {

    static final String SPECIFICATION_VERSION = "0.24";

    private static final ObjectMapper om = new ObjectMapper();

    private final String collectionId;
    private final CollectionInfo collection;
    private final String type;
    private final String kind;
    private final LocalDate created;
    private final LocalDate modified;
    private final List<CatalogEntry.Contact> maintainers;

    /**
     * @param collectionId second half of the collection's <code>aid</code>, the domain being the first
     * @param type         <code>Index</code>, <code>Collection</code> or <code>Blueprint</code>, or null to leave it out
     * @param kind         kind of entity the collection describes, or null to leave it out
     */
    public ApisJsonRenderer(String collectionId, CollectionInfo collection, String type, String kind,
                            LocalDate created, LocalDate modified, List<CatalogEntry.Contact> maintainers) {
        this.collectionId = collectionId;
        this.collection = collection;
        this.type = type;
        this.kind = kind;
        this.created = created;
        this.modified = modified;
        this.maintainers = List.copyOf(maintainers);
    }

    @Override
    public String contentType() {
        return APPLICATION_JSON;
    }

    @Override
    public byte[] render(Catalog catalog) throws JsonProcessingException {
        final var collection = om.createObjectNode();
        collection.put("aid", catalog.domain() + ":" + collectionId);
        collection.put("name", this.collection.name());
        collection.put("description", this.collection.description());
        putIfPresent(collection, "type", type);
        putIfPresent(collection, "kind", kind);
        putIfPresent(collection, "url", catalog.url());
        collection.put("created", created.toString());
        collection.put("modified", modified.toString());
        collection.put("specificationVersion", SPECIFICATION_VERSION);
        if (!maintainers.isEmpty()) {
            final var maintainerNodes = collection.putArray("maintainers");
            maintainers.forEach(maintainer -> maintainerNodes.add(renderContact(maintainer)));
        }
        // A discovery document is no API, so neither this catalog nor any other one is listed.
        final var listed = catalog.apis().stream().filter(entry -> entry.catalogType() == null).toList();
        final var tags = listed.stream().flatMap(entry -> entry.tags().stream()).distinct().toList();
        if (!tags.isEmpty())
            tags.forEach(collection.putArray("tags")::add);
        final var apis = collection.putArray("apis");
        listed.forEach(entry -> apis.add(renderApi(entry)));
        return om.writeValueAsBytes(collection);
    }

    private ObjectNode renderApi(CatalogEntry entry) {
        final var api = om.createObjectNode();
        api.put("aid", entry.aid());
        api.put("name", entry.name());
        api.put("description", entry.description());
        if (entry.details().created() != null)
            api.put("created", entry.details().created().toString());
        if (entry.details().modified() != null)
            api.put("modified", entry.details().modified().toString());
        putIfPresent(api, "humanURL", entry.links().humanUrl());
        putIfPresent(api, "baseURL", entry.endpoint().url());
        putIfPresent(api, "version", entry.version());
        renderTags(api, entry);
        renderProperties(api, entry);
        if (entry.contact() != null)
            api.putArray("contact").add(renderContact(entry.contact()));
        return api;
    }

    private void renderTags(ObjectNode api, CatalogEntry entry) {
        if (entry.tags().isEmpty())
            return;
        final var tags = api.putArray("tags");
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

    private ObjectNode renderContact(CatalogEntry.Contact contact) {
        final var vCard = om.createObjectNode();
        putIfPresent(vCard, "FN", contact.name());
        putIfPresent(vCard, "email", contact.email());
        putIfPresent(vCard, "url", contact.url());
        return vCard;
    }

    private ObjectNode renderProperty(String type, String name, String url) {
        final var property = om.createObjectNode();
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
