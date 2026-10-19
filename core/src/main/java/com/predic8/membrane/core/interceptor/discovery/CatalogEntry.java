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

import java.time.*;
import java.util.*;

/**
 * One API of a {@link Catalog}. A proxy described by several OpenAPI documents contributes one
 * entry per document, a proxy without an OpenAPI document contributes a single entry.
 *
 * @param aid            globally unique identifier, <code>&lt;domain&gt;:&lt;id&gt;</code>, or the
 *                       configured id of the api verbatim
 * @param id             identifier unique on this gateway, the same whichever host the catalog
 *                       was fetched from
 * @param kind           which kind of proxy this entry describes
 * @param name           human readable name, never null
 * @param description    human readable description, never null
 * @param version        version of the API, or null when no OpenAPI document describes it
 * @param endpoint       where the API is reachable on this gateway
 * @param links          documentation and specification URLs
 * @param tags           tags configured on the api, never null
 * @param documentTags   tags the OpenAPI document declares, in its order, which group the
 *                       operations; never null
 * @param contact        contact from the OpenAPI <code>info</code> block, or null
 * @param license        license from the OpenAPI <code>info</code> block, or null
 * @param termsOfService terms of service URL from the OpenAPI <code>info</code> block, or null
 * @param soap           WSDL details, or null unless {@link ProxyKind#SOAP_PROXY}
 * @param operations     operations of the API, never null but empty without an OpenAPI document
 * @param catalogType    media type of the catalog this api publishes through a discovery plugin,
 *                       or null when it is no catalog
 * @param details        catalog metadata configured on the api, never null
 */
public record CatalogEntry(String aid, String id, ProxyKind kind, String name, String description,
                           String version, Endpoint endpoint, Links links, List<String> tags, List<String> documentTags,
                           Contact contact, License license, String termsOfService, SoapInfo soap,
                           List<CatalogOperation> operations, String catalogType, Details details) {

    public CatalogEntry {
        tags = List.copyOf(tags);
        documentTags = List.copyOf(documentTags);
        operations = List.copyOf(operations);
    }

    public enum ProxyKind {

        API("api"),
        SERVICE_PROXY("serviceProxy"),
        SOAP_PROXY("soapProxy");

        private final String label;

        ProxyKind(String label) {
            this.label = label;
        }

        /**
         * Name of the configuration element this kind stands for, as rendered into the
         * Membrane discovery format (<code>apiDiscovery</code>).
         */
        public String getLabel() {
            return label;
        }
    }

    /**
     * Where the API is reachable on this gateway. Host and port are resolved against the incoming
     * request, so a wildcard host is reported as the host the client actually used.
     *
     * @param url origin and path combined, with the port omitted when it is the default for the protocol
     */
    public record Endpoint(String protocol, String host, int port, String path, String method,
                           String url) {}

    /**
     * @param humanUrl URL of a page a human can read, i.e. the Swagger UI or the API docs overview
     * @param specUrl  URL of the OpenAPI document, or null when no OpenAPI document describes the API
     */
    public record Links(String humanUrl, String specUrl) {}

    /**
     * A person or organization, with the members of the vCard APIs.json uses.
     */
    public record Contact(String name, String email, String url, String organization, String address,
                          String phone, String twitter, String github, String photo, String vCard) {

        public Contact(String name, String email, String url) {
            this(name, email, url, null, null, null, null, null, null, null);
        }
    }

    /**
     * A typed link or inline value, such as a pricing page, a prompt or a ruleset.
     *
     * @param url  URL of the resource, or null when <code>data</code> carries it inline
     * @param data the resource inline, or null when <code>url</code> points at it
     */
    public record Resource(String type, String name, String description, String mediaType, String url, String data,
                           List<String> tags) {

        public Resource {
            tags = List.copyOf(tags);
        }
    }

    /**
     * A link with a label, such as an overlay with its type or an included catalog with its name.
     */
    public record Reference(String label, String url) {}

    /**
     * Catalog metadata configured on the api in its <code>info</code> block.
     *
     * @param image    URL of an image representing the API, or null
     * @param created  day the API was created, or null
     * @param modified day the API last changed, or null
     */
    public record Details(String image, LocalDate created, LocalDate modified, List<Resource> properties,
                          List<Reference> overlays, List<Resource> prompts, List<Resource> rules,
                          List<Resource> workflows) {

        public static final Details NONE = new Details(null, null, null, List.of(), List.of(), List.of(), List.of(), List.of());

        public Details {
            properties = List.copyOf(properties);
            overlays = List.copyOf(overlays);
            prompts = List.copyOf(prompts);
            rules = List.copyOf(rules);
            workflows = List.copyOf(workflows);
        }
    }

    public record License(String name, String url) {}

    public record SoapInfo(String wsdl, String serviceName, String portName) {}

    public CatalogEntry withIdentifiers(String aid, String id) {
        return new CatalogEntry(aid, id, kind, name, description, version, endpoint, links, tags, documentTags,
                contact, license, termsOfService, soap, operations, catalogType, details);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Assembles an entry field by field. A {@link CatalogEntry} has too many components to build
     * readably through its canonical constructor.
     */
    public static class Builder {

        private String aid;
        private String id;
        private ProxyKind kind;
        private String name;
        private String description;
        private String version;
        private Endpoint endpoint;
        private Links links = new Links(null, null);
        private List<String> tags = List.of();
        private List<String> documentTags = List.of();
        private Contact contact;
        private License license;
        private String termsOfService;
        private SoapInfo soap;
        private List<CatalogOperation> operations = List.of();
        private String catalogType;
        private Details details = Details.NONE;

        public Builder aid(String aid) {
            this.aid = aid;
            return this;
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder kind(ProxyKind kind) {
            this.kind = kind;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder endpoint(Endpoint endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        public Builder links(Links links) {
            this.links = links;
            return this;
        }

        public Builder tags(List<String> tags) {
            this.tags = tags;
            return this;
        }

        public Builder documentTags(List<String> documentTags) {
            this.documentTags = documentTags;
            return this;
        }

        public Builder contact(Contact contact) {
            this.contact = contact;
            return this;
        }

        public Builder license(License license) {
            this.license = license;
            return this;
        }

        public Builder termsOfService(String termsOfService) {
            this.termsOfService = termsOfService;
            return this;
        }

        public Builder soap(SoapInfo soap) {
            this.soap = soap;
            return this;
        }

        public Builder operations(List<CatalogOperation> operations) {
            this.operations = operations;
            return this;
        }

        public Builder catalogType(String catalogType) {
            this.catalogType = catalogType;
            return this;
        }

        public Builder details(Details details) {
            this.details = details;
            return this;
        }

        public CatalogEntry build() {
            return new CatalogEntry(aid, id, kind, name, description, version, endpoint, links, tags, documentTags,
                    contact, license, termsOfService, soap, operations, catalogType, details);
        }
    }
}
