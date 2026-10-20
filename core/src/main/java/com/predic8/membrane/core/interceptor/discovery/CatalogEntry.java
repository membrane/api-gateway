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
 * @param aid            globally unique identifier, <code>&lt;domain&gt;:&lt;id&gt;</code>
 * @param id             identifier unique on this gateway, the same whichever host the catalog
 *                       was fetched from
 * @param name           human readable name, never null
 * @param description    human readable description, never null
 * @param version        version of the API, or null when no OpenAPI document describes it
 * @param tags           tags configured on the api, never null
 * @param contact        contact configured on the api, else from the OpenAPI <code>info</code> block, or null
 * @param license        license from the OpenAPI <code>info</code> block, or null
 * @param termsOfService terms of service URL from the OpenAPI <code>info</code> block, or null
 * @param soap           WSDL details, or null unless {@link ProxyKind#SOAP_PROXY}
 * @param catalogType    media type of the catalog this api publishes through a discovery plugin,
 *                       or null when it is no catalog
 * @param details        catalog metadata configured on the api, never null
 */
public record CatalogEntry(String aid, String id, ProxyKind kind, String name, String description,
                           String version, Endpoint endpoint, Links links, List<String> tags,
                           Contact contact, License license, String termsOfService, SoapInfo soap,
                           String catalogType, Details details) {

    public CatalogEntry {
        tags = List.copyOf(tags);
    }

    public enum ProxyKind {
        API, SERVICE_PROXY, SOAP_PROXY
    }

    /**
     * @param url origin and path combined, with the port omitted when it is the default for the protocol
     */
    public record Endpoint(String path, String method, String url) {}

    /**
     * @param humanUrl URL of a page a human can read, i.e. the Swagger UI or the API docs overview
     * @param specUrl  URL of the OpenAPI document, or null when no OpenAPI document describes the API
     */
    public record Links(String humanUrl, String specUrl) {}

    public record Contact(String name, String email, String url) {}

    public record Details(LocalDate created, LocalDate modified) {

        public static final Details NONE = new Details(null, null);
    }

    public record License(String name, String url) {}

    public record SoapInfo(String wsdl, String serviceName, String portName) {}

    public CatalogEntry withIdentifiers(String aid, String id) {
        return new CatalogEntry(aid, id, kind, name, description, version, endpoint, links, tags,
                contact, license, termsOfService, soap, catalogType, details);
    }

    public static Builder builder() {
        return new Builder();
    }

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
        private Contact contact;
        private License license;
        private String termsOfService;
        private SoapInfo soap;
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

        public Builder catalogType(String catalogType) {
            this.catalogType = catalogType;
            return this;
        }

        public Builder details(Details details) {
            this.details = details;
            return this;
        }

        public CatalogEntry build() {
            return new CatalogEntry(aid, id, kind, name, description, version, endpoint, links, tags,
                    contact, license, termsOfService, soap, catalogType, details);
        }
    }
}
