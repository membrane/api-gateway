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

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.router.*;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.*;
import io.swagger.v3.oas.models.tags.*;

import java.time.*;
import java.util.*;

import static com.predic8.membrane.core.interceptor.discovery.CatalogEntry.ProxyKind.*;
import static com.predic8.membrane.core.interceptor.discovery.CatalogEntry.builder;
import static com.predic8.membrane.core.openapi.serviceproxy.OpenAPIPublisherInterceptor.*;
import static java.lang.Boolean.*;
import static java.util.Objects.*;

/**
 * Turns the proxies deployed on a {@link Router} into a {@link Catalog}. Collection happens once
 * per request rather than once per process: proxies come and go with configuration reloads, and
 * host and port are resolved against the incoming request, so the URLs in the document are the
 * ones the calling client can actually reach.
 * <p>
 * Only proxies that open a port are listed. <code>internal</code> proxies
 * ({@link NotPortOpeningProxy}) are reachable through <code>call</code> alone and are therefore
 * not part of the published surface, and a proxy whose initialisation failed is skipped as well.
 */
public class CatalogCollector {

    private static final String DEFAULT_PATH = "/";
    private static final String WILDCARD_HOST = "*";

    private final String rootDomain;
    private final String collectionId;
    private final String collectionName;
    private final String description;
    private final String configuredUrl;
    private final LocalDate created;
    private final LocalDate modified;
    private final boolean collectOperations;

    /**
     * @param collectOperations whether to walk the paths of every OpenAPI document. Only the
     *                          <code>membrane</code> format renders operations, and walking them is
     *                          the only part of collecting that grows with the size of the
     *                          documents, so the other formats switch it off.
     */
    public CatalogCollector(String rootDomain, String collectionId, String collectionName, String description, String configuredUrl, LocalDate created, LocalDate modified, boolean collectOperations) {
        this.rootDomain = rootDomain;
        this.collectionId = collectionId;
        this.collectionName = collectionName;
        this.description = description;
        this.configuredUrl = configuredUrl;
        this.created = created;
        this.modified = modified;
        this.collectOperations = collectOperations;
    }

    public Catalog collect(Router router, Exchange exchange) {
        return new Catalog(rootDomain + ":" + collectionId, collectionName, description, catalogUrl(exchange), created, modified, collectEntries(router, exchange));
    }

    /**
     * URL of the catalog document itself. The configured value wins; without one the URL is
     * derived from the request that asked for the document, with any query string dropped.
     */
    private String catalogUrl(Exchange exchange) {
        if (configuredUrl != null) return configuredUrl;
        if (!(exchange.getProxy() instanceof AbstractServiceProxy serviceProxy)) return null;
        return origin(serviceProxy, exchange) + pathWithoutQuery(exchange);
    }

    private static String pathWithoutQuery(Exchange exchange) {
        String uri = exchange.getRequest().getUri();
        if (uri == null) return DEFAULT_PATH;
        int query = uri.indexOf('?');
        return query == -1 ? uri : uri.substring(0, query);
    }

    private List<CatalogEntry> collectEntries(Router router, Exchange exchange) {
        List<CatalogEntry> entries = new ArrayList<>();
        // Snapshot: getRules() hands out the live list, which a configuration reload mutates.
        for (Proxy proxy : List.copyOf(router.getRuleManager().getRules())) {
            if (!(proxy instanceof AbstractServiceProxy serviceProxy)) continue;
            if (proxy instanceof NotPortOpeningProxy || !proxy.isActive()) continue;
            entries.addAll(entriesFor(serviceProxy, exchange));
        }
        return entries;
    }

    /**
     * An API described by several OpenAPI documents contributes one entry per document, because
     * each document is a separately published, separately versioned API.
     */
    private List<CatalogEntry> entriesFor(AbstractServiceProxy proxy, Exchange exchange) {
        if (proxy instanceof APIProxy api && !api.getApiRecords().isEmpty())
            return api.getApiRecords().entrySet().stream().map(record -> describedEntry(api, record.getKey(), record.getValue(), exchange)).toList();
        return List.of(plainEntry(proxy, exchange));
    }

    private CatalogEntry describedEntry(APIProxy api, String recordId, OpenAPIRecord record, Exchange exchange) {
        OpenAPI openAPI = record.getApi();
        Info info = openAPI.getInfo();
        String origin = origin(api, exchange);
        return builder()
                .aid(aid(api, recordId))
                .kind(API).name(name(api, info))
                .description(description(api, info))
                .version(info == null ? null : info.getVersion())
                .endpoint(endpoint(api, exchange, basePathOf(api, record)))
                .links(new CatalogEntry.Links(origin + PATH_UI + "/" + recordId, origin + PATH + "/" + recordId))
                .tags(tags(openAPI)).contact(contact(info))
                .license(license(info))
                .termsOfService(info == null ? null : info.getTermsOfService())
                .operations(collectOperations ? operations(openAPI) : List.of()).build();
    }

    /**
     * An entry for a proxy no OpenAPI document describes. It carries no documentation links: a
     * proxy without an OpenAPI document publishes neither <code>/api-docs</code> nor a Swagger UI,
     * so any link here would be dead.
     */
    private CatalogEntry plainEntry(AbstractServiceProxy proxy, Exchange exchange) {
        return builder().aid(aid(proxy, null)).kind(kindOf(proxy)).name(proxy.getName()).description(description(proxy, null)).endpoint(endpoint(proxy, exchange, pathOf(proxy))).soap(soapInfo(proxy)).build();
    }

    private static CatalogEntry.ProxyKind kindOf(AbstractServiceProxy proxy) {
        if (proxy instanceof APIProxy) return API;
        if (proxy instanceof SOAPProxy) return SOAP_PROXY;
        return SERVICE_PROXY;
    }

    private static CatalogEntry.SoapInfo soapInfo(AbstractServiceProxy proxy) {
        if (!(proxy instanceof SOAPProxy soap)) return null;
        return new CatalogEntry.SoapInfo(requireNonNullElse(soap.getResolvedWsdl(), soap.getWsdl()), soap.getServiceName(), soap.getPortName());
    }

    private CatalogEntry.Endpoint endpoint(AbstractServiceProxy proxy, Exchange exchange, String path) {
        RuleKey key = proxy.getKey();
        String protocol = proxy.getProtocol();
        String host = hostOf(key, exchange);
        int port = portOf(key, protocol);
        return new CatalogEntry.Endpoint(protocol, host, port, path, key.getMethod(), origin(protocol, host, port) + path);
    }

    private String origin(AbstractServiceProxy proxy, Exchange exchange) {
        String protocol = proxy.getProtocol();
        return origin(protocol, hostOf(proxy.getKey(), exchange), portOf(proxy.getKey(), protocol));
    }

    /**
     * The port is part of the URL unless it is the default for the protocol. Checking the protocol
     * matters: an HTTPS API on port 80 needs the port, an HTTP one on 80 does not.
     */
    private static String origin(String protocol, String host, int port) {
        if (("http".equals(protocol) && port == 80) || ("https".equals(protocol) && port == 443))
            return protocol + "://" + host;
        return protocol + "://" + host + ":" + port;
    }

    /**
     * A proxy bound to every host is reported under the host the client used, so that the
     * published URLs work for whoever fetched the document. Falls back to the address the
     * connection was accepted on, and finally to <code>localhost</code> when there is neither a
     * Host header nor a connection, as in unit tests.
     */
    private static String hostOf(RuleKey key, Exchange exchange) {
        if (key.getHost() != null && !WILDCARD_HOST.equals(key.getHost())) return key.getHost();
        String hostHeader = exchange.getOriginalHostHeader();
        if (hostHeader != null && !hostHeader.isBlank()) return hostWithoutPort(hostHeader);
        if (exchange.getHandler() != null) return exchange.getHandler().getLocalAddress().getHostAddress();
        return "localhost";
    }

    /**
     * Host part of a <code>Host</code> header. An IPv6 literal arrives bracketed
     * (<code>[::1]</code>) and keeps its brackets, because that is the form a URL needs. The colons
     * inside it are not port separators, so a port only counts when it follows the closing bracket,
     * or when an unbracketed value carries exactly one colon.
     */
    private static String hostWithoutPort(String hostHeader) {
        if (hostHeader.startsWith("[")) {
            int bracket = hostHeader.indexOf(']');
            return bracket == -1 ? hostHeader : hostHeader.substring(0, bracket + 1);
        }
        int colon = hostHeader.indexOf(':');
        if (colon == -1 || colon != hostHeader.lastIndexOf(':')) return hostHeader;
        return hostHeader.substring(0, colon);
    }

    private static int portOf(RuleKey key, String protocol) {
        if (key.getPort() != -1) return key.getPort();
        return "https".equals(protocol) ? 443 : 80;
    }

    /**
     * Path the given OpenAPI document is served under. The explicitly configured path of the api
     * wins; otherwise the base path Membrane derived from the document's <code>servers</code>.
     */
    private static String basePathOf(APIProxy api, OpenAPIRecord record) {
        if (api.getKey().getPath() != null) return api.getKey().getPath();
        if (api.getBasePaths() == null) return DEFAULT_PATH;
        return api.getBasePaths().entrySet().stream().filter(basePath -> basePath.getValue() == record).map(Map.Entry::getKey).findFirst().orElse(DEFAULT_PATH);
    }

    private static String pathOf(AbstractServiceProxy proxy) {
        return requireNonNullElse(proxy.getKey().getPath(), DEFAULT_PATH);
    }

    /**
     * Identifier of the entry, <code>&lt;rootDomain&gt;:&lt;slug&gt;</code>. An api that sets
     * <code>id</code> keeps that value verbatim, so configuration can pin an identifier that
     * outlives a change of host, port or path.
     */
    private String aid(AbstractServiceProxy proxy, String recordId) {
        if (proxy instanceof APIProxy api && api.getId() != null) return api.getId();
        if (recordId != null) return rootDomain + ":" + recordId;
        return rootDomain + ":" + slug(keyId(proxy));
    }

    private static String keyId(AbstractServiceProxy proxy) {
        if (proxy.getKey() instanceof APIProxyKey apiKey) return apiKey.getKeyId();
        return proxy.getKey().toString();
    }

    /**
     * Key ids such as <code>*-0.0.0.0-*80/baz</code> make poor identifiers, so everything that is
     * not a letter or a digit collapses into a single dash.
     */
    static String slug(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }

    private static String name(AbstractServiceProxy proxy, Info info) {
        if (info != null && info.getTitle() != null && !info.getTitle().isBlank()) return info.getTitle();
        return proxy.getName();
    }

    /**
     * A description configured on the api wins over the one in its OpenAPI document, so that
     * configuration can override what the document says. APIs.json requires the member, hence the
     * fall back to the name.
     */
    private static String description(AbstractServiceProxy proxy, Info info) {
        if (proxy instanceof APIProxy api && api.getDescription() != null && api.getDescription().getContent() != null)
            return api.getDescription().getContent();
        if (info != null && info.getDescription() != null && !info.getDescription().isBlank())
            return info.getDescription();
        return name(proxy, info);
    }

    private static List<String> tags(OpenAPI api) {
        if (api.getTags() == null) return List.of();
        return api.getTags().stream().map(Tag::getName).filter(Objects::nonNull).distinct().toList();
    }

    private static CatalogEntry.Contact contact(Info info) {
        if (info == null || info.getContact() == null) return null;
        Contact contact = info.getContact();
        return new CatalogEntry.Contact(contact.getName(), contact.getEmail(), contact.getUrl());
    }

    private static CatalogEntry.License license(Info info) {
        if (info == null || info.getLicense() == null) return null;
        License license = info.getLicense();
        return new CatalogEntry.License(license.getName(), license.getUrl());
    }

    private static List<CatalogOperation> operations(OpenAPI api) {
        if (api.getPaths() == null) return List.of();
        List<CatalogOperation> operations = new ArrayList<>();
        api.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, operation) -> operations.add(new CatalogOperation(method.name(), path, operation.getOperationId(), operation.getSummary(), requireNonNullElse(operation.getTags(), List.<String>of()), TRUE.equals(operation.getDeprecated())))));
        return operations;
    }
}
