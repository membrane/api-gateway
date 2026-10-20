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

import java.net.URI;
import java.util.*;

import static com.predic8.membrane.core.interceptor.discovery.CatalogEntry.ProxyKind.*;
import static com.predic8.membrane.core.interceptor.discovery.CatalogEntry.builder;
import static com.predic8.membrane.core.openapi.serviceproxy.OpenAPIPublisher.*;
import static com.predic8.membrane.core.openapi.util.UriUtil.*;
import static java.util.Objects.*;

/**
 * Turns the proxies deployed on a {@link Router} into a {@link Catalog}. Collection happens once
 * per request rather than once per process: proxies come and go with configuration reloads, and
 * host and port are resolved against the incoming request, so the URLs in the document are the
 * ones the calling client can actually reach.
 * <p>
 * A configured base URL replaces the request wherever the request would have been asked: it is the
 * origin of the catalog URL and of every proxy bound to all hosts, and its host is the domain
 * identifiers are built from. That is what a gateway behind a reverse proxy needs, whose clients
 * reach it under a different origin than the one Membrane sees. A proxy bound to one host keeps
 * that host, because it answers on no other.
 * <p>
 * Only proxies that open a port are listed. <code>internal</code> proxies
 * ({@link NotPortOpeningProxy}) are reachable through <code>call</code> alone and are therefore
 * not part of the published surface, and a proxy whose initialisation failed is skipped as well.
 */
public class CatalogCollector {

    private static final String DEFAULT_PATH = "/";
    private static final String WILDCARD_HOST = "*";

    private final String baseUrl;
    private final URI baseUri;
    private final List<TagRule> tagRules;

    /**
     * @param baseUrl  absolute URL clients reach the gateway under, such as
     *                 <code>https://api.example.com</code>, or null to take everything from the
     *                 request
     */
    public CatalogCollector(String baseUrl, List<TagRule> tagRules) {
        this.baseUrl = trimTrailingSlash(withLowercaseScheme(baseUrl));
        this.baseUri = this.baseUrl == null ? null : URI.create(this.baseUrl);
        this.tagRules = List.copyOf(tagRules);
    }

    /**
     * Schemes are case-insensitive, but everything here compares them against <code>http</code>
     * and <code>https</code>, so the base URL is normalised once.
     */
    private static String withLowercaseScheme(String url) {
        if (url == null) return null;
        final var colon = url.indexOf(':');
        if (colon == -1) return url;
        return url.substring(0, colon).toLowerCase(Locale.ROOT) + url.substring(colon);
    }

    public Catalog collect(Router router, Exchange exchange) {
        final var domain = domain(exchange);
        return new Catalog(domain, catalogUrl(exchange), collectEntries(router, exchange, domain));
    }

    private String domain(Exchange exchange) {
        if (baseUri != null) return baseUri.getHost();
        return requestHost(exchange);
    }

    private String catalogUrl(Exchange exchange) {
        if (baseUrl != null) return baseUrl + pathWithoutQuery(exchange);
        if (exchange.getProxy() instanceof AbstractServiceProxy serviceProxy)
            return origin(serviceProxy, exchange) + pathWithoutQuery(exchange);
        String hostHeader = exchange.getOriginalHostHeader();
        String authority = hostHeader != null && !hostHeader.isBlank() ? hostHeader : requestHost(exchange);
        return "http://" + authority + pathWithoutQuery(exchange);
    }

    private static String pathWithoutQuery(Exchange exchange) {
        String uri = exchange.getRequest().getUri();
        if (uri == null) return DEFAULT_PATH;
        int query = uri.indexOf('?');
        return query == -1 ? uri : uri.substring(0, query);
    }

    private List<CatalogEntry> collectEntries(Router router, Exchange exchange, String domain) {
        List<CatalogEntry> entries = new ArrayList<>();
        final var taken = new HashSet<String>();
        // Snapshot: getRules() hands out the live list, which a configuration reload mutates.
        for (Proxy proxy : List.copyOf(router.getRuleManager().getRules())) {
            if (!(proxy instanceof AbstractServiceProxy serviceProxy)) continue;
            if (proxy instanceof NotPortOpeningProxy || !proxy.isActive()) continue;
            if (!TagRule.isListed(tagRules, serviceProxy.getTags())) continue;
            for (CatalogEntry entry : entriesFor(serviceProxy, exchange, domain))
                entries.add(unique(entry, serviceProxy, domain, taken));
        }
        return entries;
    }

    private static CatalogEntry unique(CatalogEntry entry, AbstractServiceProxy proxy, String domain, Set<String> taken) {
        if (taken.add(entry.id())) return entry;
        final var base = entry.id() + "-" + slug(keyId(proxy));
        var id = base;
        for (int n = 2; !taken.add(id); n++)
            id = base + "-" + n;
        return entry.withIdentifiers(domain + ":" + id, id);
    }

    /**
     * An API described by several OpenAPI documents contributes one entry per document, because
     * each document is a separately published, separately versioned API.
     */
    private List<CatalogEntry> entriesFor(AbstractServiceProxy proxy, Exchange exchange, String domain) {
        if (proxy instanceof APIProxy api && !api.getApiRecords().isEmpty())
            return api.getApiRecords().entrySet().stream().map(record -> describedEntry(api, record.getKey(), record.getValue(), exchange, domain)).toList();
        return List.of(plainEntry(proxy, exchange, domain));
    }

    private CatalogEntry describedEntry(APIProxy api, String recordId, OpenAPIRecord record, Exchange exchange, String domain) {
        OpenAPI openAPI = record.getApi();
        Info info = openAPI.getInfo();
        String origin = origin(api, exchange);
        return builder()
                .aid(aid(api, recordId, domain)).id(id(api, recordId))
                .kind(API).name(name(api, info))
                .description(description(api, info))
                .version(info == null ? null : info.getVersion())
                .endpoint(endpoint(api, exchange, basePathOf(api, record)))
                .links(new CatalogEntry.Links(origin + PATH_UI + "/" + recordId, origin + PATH + "/" + recordId))
                .tags(api.getTags())
                .contact(configuredContact(api) != null ? configuredContact(api) : contact(info))
                .license(license(info))
                .termsOfService(info == null ? null : info.getTermsOfService())
                .catalogType(catalogType(api))
                .details(details(api)).build();
    }

    /**
     * No documentation links: a proxy without an OpenAPI document publishes neither
     * <code>/api-docs</code> nor a Swagger UI, so any link here would be dead.
     */
    private CatalogEntry plainEntry(AbstractServiceProxy proxy, Exchange exchange, String domain) {
        CatalogEntry.Endpoint endpoint = endpoint(proxy, exchange, pathOf(proxy));
        return builder().aid(aid(proxy, null, domain)).id(id(proxy, null)).kind(kindOf(proxy)).name(proxy.getName()).description(description(proxy, null)).endpoint(endpoint).tags(proxy.getTags()).contact(configuredContact(proxy)).soap(soapInfo(proxy, endpoint)).catalogType(catalogType(proxy)).details(details(proxy)).build();
    }

    /**
     * A discovery plugin answers every request and ends the flow, so it sits at the top level.
     */
    private static String catalogType(AbstractServiceProxy proxy) {
        return proxy.getFlow().stream()
                .filter(AbstractApiDiscoveryInterceptor.class::isInstance)
                .map(interceptor -> ((AbstractApiDiscoveryInterceptor) interceptor).catalogType())
                .findFirst().orElse(null);
    }

    private static CatalogEntry.ProxyKind kindOf(AbstractServiceProxy proxy) {
        if (proxy instanceof APIProxy) return API;
        if (proxy instanceof SOAPProxy) return SOAP_PROXY;
        return SERVICE_PROXY;
    }

    /**
     * Points at the WSDL the gateway publishes under <code>?wsdl</code>, with the service address
     * rewritten to the gateway. The configured WSDL location usually names the backend, which the
     * client cannot reach and which a public catalog must not reveal.
     */
    private static CatalogEntry.SoapInfo soapInfo(AbstractServiceProxy proxy, CatalogEntry.Endpoint endpoint) {
        if (!(proxy instanceof SOAPProxy soap)) return null;
        return new CatalogEntry.SoapInfo(endpoint.url() + "?wsdl", soap.getServiceName(), soap.getPortName());
    }

    private CatalogEntry.Endpoint endpoint(AbstractServiceProxy proxy, Exchange exchange, String path) {
        RuleKey key = proxy.getKey();
        if (usesBaseUrl(key))
            return new CatalogEntry.Endpoint(baseUri.getScheme(), baseUri.getHost(), portOf(baseUri), path, key.getMethod(), baseUrl + path);
        String protocol = proxy.getProtocol();
        String host = hostOf(key, exchange);
        int port = portOf(key, protocol);
        return new CatalogEntry.Endpoint(protocol, host, port, path, key.getMethod(), origin(protocol, host, port) + path);
    }

    private String origin(AbstractServiceProxy proxy, Exchange exchange) {
        if (usesBaseUrl(proxy.getKey())) return baseUrl;
        String protocol = proxy.getProtocol();
        return origin(protocol, hostOf(proxy.getKey(), exchange), portOf(proxy.getKey(), protocol));
    }

    /**
     * The base URL stands in for the request host, so it applies exactly where that would: to a
     * proxy bound to every host.
     */
    private boolean usesBaseUrl(RuleKey key) {
        return baseUrl != null && isBoundToEveryHost(key);
    }

    private static boolean isBoundToEveryHost(RuleKey key) {
        return key.getHost() == null || WILDCARD_HOST.equals(key.getHost());
    }

    private static int portOf(URI uri) {
        if (uri.getPort() != -1) return uri.getPort();
        return "https".equals(uri.getScheme()) ? 443 : 80;
    }

    private static String origin(String protocol, String host, int port) {
        if (("http".equals(protocol) && port == 80) || ("https".equals(protocol) && port == 443))
            return protocol + "://" + host;
        return protocol + "://" + host + ":" + port;
    }

    /**
     * A proxy bound to every host is reported under the host the client used, so that the
     * published URLs work for whoever fetched the document. Falls back to the address the
     * connection was accepted on, and finally to <code>localhost</code> when there is neither a
     * Host header nor a local address, as in unit tests or a resent exchange.
     */
    private static String hostOf(RuleKey key, Exchange exchange) {
        if (!isBoundToEveryHost(key)) return key.getHost();
        return requestHost(exchange);
    }

    private static String requestHost(Exchange exchange) {
        String hostHeader = exchange.getOriginalHostHeader();
        if (hostHeader != null && !hostHeader.isBlank()) return hostWithoutPort(hostHeader);
        if (exchange.getHandler() != null && exchange.getHandler().getLocalAddress() != null)
            return exchange.getHandler().getLocalAddress().getHostAddress();
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

    private static String basePathOf(APIProxy api, OpenAPIRecord record) {
        if (api.getKey().getPath() != null) return api.getKey().getPath();
        if (api.getBasePaths() == null) return DEFAULT_PATH;
        return api.getBasePaths().entrySet().stream().filter(basePath -> basePath.getValue() == record).map(Map.Entry::getKey).findFirst().orElse(DEFAULT_PATH);
    }

    private static String pathOf(AbstractServiceProxy proxy) {
        return requireNonNullElse(proxy.getKey().getPath(), DEFAULT_PATH);
    }

    /**
     * Globally unique identifier of the entry, <code>&lt;domain&gt;:&lt;id&gt;</code>, the form
     * APIs.json requires. A configured <code>id</code> replaces only the second half, so together
     * with a base URL it pins an identifier that outlives a change of host, port or path.
     */
    private static String aid(AbstractServiceProxy proxy, String recordId, String domain) {
        return domain + ":" + id(proxy, recordId);
    }

    /**
     * With several OpenAPI documents the document id is appended to a configured id, because every
     * entry needs an identifier of its own.
     */
    private static String id(AbstractServiceProxy proxy, String recordId) {
        if (proxy instanceof APIProxy api && api.getId() != null) {
            if (recordId != null && api.getApiRecords().size() > 1) return api.getId() + ":" + recordId;
            return api.getId();
        }
        if (recordId != null) return recordId;
        return slug(keyId(proxy));
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

    private static CatalogEntry.Contact configuredContact(AbstractServiceProxy proxy) {
        if (proxy.getInfo() == null || proxy.getInfo().getContact() == null) return null;
        final var contact = proxy.getInfo().getContact();
        return new CatalogEntry.Contact(contact.getName(), contact.getEmail(), contact.getUrl());
    }

    private static CatalogEntry.Details details(AbstractServiceProxy proxy) {
        if (proxy.getInfo() == null) return CatalogEntry.Details.NONE;
        return new CatalogEntry.Details(proxy.getInfo().getCreated(), proxy.getInfo().getModified());
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

}
