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

    private final CollectionInfo collection;
    private final ApisJsonCollection settings;

    /**
     * @param collection name and description of the collection
     * @param settings   every other configured member of the collection
     */
    public ApisJsonRenderer(CollectionInfo collection, ApisJsonCollection settings) {
        this.collection = collection;
        this.settings = settings;
    }

    @Override
    public String contentType() {
        return APPLICATION_JSON;
    }

    @Override
    public byte[] render(Catalog catalog) throws JsonProcessingException {
        ObjectNode collection = om.createObjectNode();
        collection.put("aid", catalog.domain() + ":" + settings.id());
        collection.put("name", this.collection.name());
        collection.put("description", this.collection.description());
        putIfPresent(collection, "image", settings.image());
        putIfPresent(collection, "url", catalog.url());
        // A discovery document is no API, so neither this catalog nor any other one is listed.
        final var listed = catalog.apis().stream().filter(entry -> entry.catalogType() == null).toList();
        final var tags = listed.stream().flatMap(entry -> entry.tags().stream()).distinct().toList();
        putStrings(collection, "tags", tags);
        collection.put("created", settings.created().toString());
        collection.put("modified", settings.modified().toString());
        putIfPresent(collection, "type", settings.type());
        putIfPresent(collection, "kind", settings.kind());
        putIfPresent(collection, "position", settings.position());
        putIfPresent(collection, "access", settings.access());
        collection.put("specificationVersion", SPECIFICATION_VERSION);
        ArrayNode apis = collection.putArray("apis");
        listed.forEach(entry -> apis.add(renderApi(entry)));
        putResources(collection, "common", settings.common());
        putResources(collection, "prompts", settings.prompts());
        putResources(collection, "rules", settings.rules());
        putResources(collection, "workflows", settings.workflows());
        putReferences(collection, "overlays", "type", settings.overlays());
        putReferences(collection, "include", "name", settings.include());
        putReferences(collection, "networks", "name", settings.networks());
        putContacts(collection, "maintainers", settings.maintainers());
        return om.writeValueAsBytes(collection);
    }

    private ObjectNode renderApi(CatalogEntry entry) {
        final var details = entry.details();
        ObjectNode api = om.createObjectNode();
        api.put("aid", entry.aid());
        api.put("name", entry.name());
        api.put("description", entry.description());
        putIfPresent(api, "image", details.image());
        putIfPresent(api, "created", details.created() == null ? null : details.created().toString());
        putIfPresent(api, "modified", details.modified() == null ? null : details.modified().toString());
        putIfPresent(api, "humanURL", entry.links().humanUrl());
        putIfPresent(api, "baseURL", entry.endpoint().url());
        putIfPresent(api, "version", entry.version());
        putStrings(api, "tags", entry.tags());
        renderProperties(api, entry);
        putReferences(api, "overlays", "type", details.overlays());
        putResources(api, "prompts", details.prompts());
        putResources(api, "rules", details.rules());
        putResources(api, "workflows", details.workflows());
        putContacts(api, "contact", entry.contact() == null ? List.of() : List.of(entry.contact()));
        return api;
    }

    /**
     * Only APIs with something to link get a <code>properties</code> array. An empty array would
     * be noise: the whole point of the member is to carry machine readable links. The links
     * Membrane derives come first, the configured properties after them.
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
        entry.details().properties().forEach(property -> properties.add(renderResource(property)));
        if (!properties.isEmpty())
            api.set("properties", properties);
    }

    private void putResources(ObjectNode parent, String member, List<CatalogEntry.Resource> resources) {
        if (resources.isEmpty())
            return;
        final var array = parent.putArray(member);
        resources.forEach(resource -> array.add(renderResource(resource)));
    }

    private ObjectNode renderResource(CatalogEntry.Resource resource) {
        final var node = om.createObjectNode();
        putIfPresent(node, "name", resource.name());
        putIfPresent(node, "description", resource.description());
        node.put("type", resource.type());
        putIfPresent(node, "mediaType", resource.mediaType());
        putIfPresent(node, "url", resource.url());
        putIfPresent(node, "data", resource.data());
        putStrings(node, "tags", resource.tags());
        return node;
    }

    /**
     * Overlays label their link with a type, included collections and networks with a name.
     */
    private void putReferences(ObjectNode parent, String member, String labelMember, List<CatalogEntry.Reference> references) {
        if (references.isEmpty())
            return;
        final var array = parent.putArray(member);
        references.forEach(reference -> {
            final var node = array.addObject();
            putIfPresent(node, labelMember, reference.label());
            putIfPresent(node, "url", reference.url());
        });
    }

    private void putContacts(ObjectNode parent, String member, List<CatalogEntry.Contact> contacts) {
        if (contacts.isEmpty())
            return;
        final var array = parent.putArray(member);
        contacts.forEach(contact -> array.add(renderContact(contact)));
    }

    /**
     * Member names follow the examples of the specification (<code>FN</code>,
     * <code>X-twitter</code>) where they differ from its field list, as for <code>humanURL</code>.
     */
    private ObjectNode renderContact(CatalogEntry.Contact contact) {
        final var vCard = om.createObjectNode();
        putIfPresent(vCard, "FN", contact.name());
        putIfPresent(vCard, "email", contact.email());
        putIfPresent(vCard, "url", contact.url());
        putIfPresent(vCard, "org", contact.organization());
        putIfPresent(vCard, "adr", contact.address());
        putIfPresent(vCard, "tel", contact.phone());
        putIfPresent(vCard, "X-twitter", contact.twitter());
        putIfPresent(vCard, "X-github", contact.github());
        putIfPresent(vCard, "photo", contact.photo());
        putIfPresent(vCard, "vCard", contact.vCard());
        return vCard;
    }

    private static void putStrings(ObjectNode parent, String member, List<String> values) {
        if (values.isEmpty())
            return;
        final var array = parent.putArray(member);
        values.forEach(array::add);
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
