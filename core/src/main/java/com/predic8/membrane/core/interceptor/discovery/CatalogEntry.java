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

import java.util.*;

/**
 * One API of a {@link Catalog}. A proxy described by several OpenAPI documents contributes one
 * entry per document, a proxy without an OpenAPI document contributes a single entry.
 *
 * @param aid            unique identifier, <code>&lt;rootDomain&gt;:&lt;slug&gt;</code>
 * @param kind           which kind of proxy this entry describes
 * @param name           human readable name, never null
 * @param description    human readable description, never null
 * @param version        version of the API, or null when no OpenAPI document describes it
 * @param endpoint       where the API is reachable on this gateway
 * @param links          documentation and specification URLs
 * @param tags           tags of the API, never null
 * @param contact        contact from the OpenAPI <code>info</code> block, or null
 * @param license        license from the OpenAPI <code>info</code> block, or null
 * @param termsOfService terms of service URL from the OpenAPI <code>info</code> block, or null
 * @param soap           WSDL details, or null unless {@link ProxyKind#SOAP_PROXY}
 * @param operations     operations of the API, never null but empty without an OpenAPI document
 */
public record CatalogEntry(String aid, ProxyKind kind, String name, String description,
                           String version, Endpoint endpoint, Links links, List<String> tags,
                           Contact contact, License license, String termsOfService, SoapInfo soap,
                           List<CatalogOperation> operations) {

    public CatalogEntry {
        tags = List.copyOf(tags);
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
         * <code>membrane</code> discovery format.
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

    public record Contact(String name, String email, String url) {}

    public record License(String name, String url) {}

    public record SoapInfo(String wsdl, String serviceName, String portName) {}

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Assembles an entry field by field. A {@link CatalogEntry} has too many components to build
     * readably through its canonical constructor.
     */
    public static class Builder {

        private String aid;
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
        private List<CatalogOperation> operations = List.of();

        public Builder aid(String aid) {
            this.aid = aid;
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

        public Builder operations(List<CatalogOperation> operations) {
            this.operations = operations;
            return this;
        }

        public CatalogEntry build() {
            return new CatalogEntry(aid, kind, name, description, version, endpoint, links, tags,
                    contact, license, termsOfService, soap, operations);
        }
    }
}
