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

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.util.*;
import org.slf4j.*;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;

import java.time.*;
import java.time.format.*;
import java.util.*;

import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.Response.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.interceptor.discovery.DiscoveryFormat.*;

/**
 * @description Publishes every API deployed on this gateway as a machine readable catalog, so that a
 * portal, a registry or a developer discovers them all under a single URL. The catalog is collected
 * from the running configuration on each request and therefore never goes stale.
 * <p>
 * Three catalog formats are available. APIs.json 0.18 (<code>apisjson</code>) is a collection
 * document a crawler can index. RFC 9727 api-catalog (<code>apicatalog</code>) is a linkset of the
 * same APIs, served as <code>application/linkset+json</code> and expected under the well-known path
 * <code>/.well-known/api-catalog</code>. Membrane's own format (<code>membrane</code>) carries
 * everything the gateway knows, including the method and path of every operation.
 * </p>
 * <p>
 * An API described by an OpenAPI document is listed with the title, description and version from
 * that document, plus links to the document itself and to its Swagger UI. Any other proxy is listed
 * with its own name and URL. Internal proxies and proxies whose initialisation failed are left out.
 * </p>
 * <p>
 * With the <code>membrane</code> format, a browser, or any client whose <code>Accept</code> header
 * asks for HTML, gets an HTML page of the same catalog instead, operations included, with links
 * to the Swagger UI of every API. APIs.json and RFC 9727 always answer with their JSON.
 * </p>
 * <p>
 * Answers every request that reaches it and ends the flow, so give it an api of its own.
 * </p>
 * <pre>
 * apiDiscovery:
 *   [ format: apisjson | apicatalog | membrane ]
 *   [ rootDomain: &lt;domain&gt; ]
 *   [ collectionId: &lt;id&gt; ]
 *   [ name: &lt;name&gt; ]
 *   [ description: &lt;text&gt; ]
 *   [ url: &lt;url&gt; ]
 *   [ created: &lt;yyyy-mm-dd&gt; ]
 *   [ modified: &lt;yyyy-mm-dd&gt; ]
 * </pre>
 * @topic 5. OpenAPI
 * @yaml <pre><code>
 * api:
 *   port: 2000
 *   path: /.well-known/api-catalog
 *   flow:
 *     - apiDiscovery:
 *         format: apicatalog
 *         rootDomain: example.com
 *         collectionId: gateway
 * </code></pre>
 */
@MCElement(name = "apiDiscovery")
public class ApiDiscoveryInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ApiDiscoveryInterceptor.class);

    /**
     * Well-known URI RFC 9727 reserves for the api-catalog document.
     */
    static final String WELL_KNOWN_PATH = "/.well-known/api-catalog";

    private static final CatalogRenderer HTML_RENDERER = new HtmlCatalogRenderer();

    private DiscoveryFormat format = APISJSON;
    private String rootDomain = "membrane";
    private String collectionId = "apis";
    private String collectionName = "APIs";
    private String description = "APIs served by this gateway";
    private String url;
    private LocalDate created = LocalDate.now();
    private LocalDate modified = LocalDate.now();

    private CatalogRenderer renderer;
    private CatalogCollector collector;

    @Override
    public void init() {
        super.init();
        renderer = switch (format) {
            case APISJSON -> new ApisJsonRenderer();
            case APICATALOG -> new ApiCatalogRenderer();
            case MEMBRANE -> new MembraneCatalogRenderer();
        };
        collector = new CatalogCollector(rootDomain, collectionId, collectionName, description,
                url, created, modified, renderer.rendersOperations());
        warnIfNotAtWellKnownPath();
    }

    /**
     * RFC 9727 ties the api-catalog document to one well-known URI, so a linkset served anywhere
     * else will not be found by a client that follows the RFC. The proxy is unknown when the
     * plugin sits in the else branch of an {@code if}, in which case there is nothing to check.
     */
    private void warnIfNotAtWellKnownPath() {
        if (format != APICATALOG || getProxy() == null || getProxy().getKey() == null)
            return;
        String path = getProxy().getKey().getPath();
        if (!WELL_KNOWN_PATH.equals(path))
            log.warn("apiDiscovery publishes an RFC 9727 api-catalog under the path {}, but RFC 9727 "
                     + "clients look for it at {}. Set that path on the surrounding api.",
                    path == null ? "/" : path, WELL_KNOWN_PATH);
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        try {
            Catalog catalog = collector.collect(router, exc);
            CatalogRenderer negotiated = format == MEMBRANE && acceptsHtml(exc) ? HTML_RENDERER : renderer;
            Response.ResponseBuilder response = ok().contentType(negotiated.contentType());
            // The membrane format answers with HTML or JSON, so caches must key on Accept.
            if (format == MEMBRANE)
                response.header(VARY, ACCEPT);
            if (catalog.url() != null)
                response.header(LINK, "<%s>; rel=\"api-catalog\"".formatted(catalog.url()));
            // RFC 9727 requires HEAD on the catalog to answer with the headers but no document.
            exc.setResponse((exc.getRequest().isHEADRequest()
                    ? response.bodyEmpty()
                    : response.body(negotiated.render(catalog))).build());
            return RETURN;
        } catch (Exception e) {
            internal(router.getConfiguration().isProduction(), getDisplayName())
                    .detail("Could not render the API discovery document!")
                    .exception(e)
                    .buildAndSetResponse(exc);
            return ABORT;
        }
    }

    /**
     * Whether <code>text/html</code> is among the media types the client prefers most. Media types
     * are compared case-insensitively, and a quality of 0 means "not this". A malformed header
     * counts as not asking for HTML, so the client still gets the JSON.
     */
    private static boolean acceptsHtml(Exchange exc) {
        String accept = exc.getRequest().getHeader().getAccept();
        if (accept == null)
            return false;
        try {
            List<MediaType> types = MimeType.sortMimeTypeByQualityFactorDescending(accept);
            if (types.isEmpty())
                return false;
            double preferred = types.getFirst().getQualityValue();
            if (preferred <= 0.0)
                return false;
            return types.stream()
                    .filter(type -> type.getQualityValue() == preferred)
                    .anyMatch(MediaType.TEXT_HTML::equalsTypeAndSubtype);
        } catch (InvalidMediaTypeException e) {
            log.debug("Ignoring malformed Accept header '{}'.", accept);
            return false;
        }
    }

    public DiscoveryFormat getFormat() {
        return format;
    }

    /**
     * @description Format of the published document. Constant names are matched case-insensitively.
     * @default apisjson
     * @example apicatalog
     */
    @MCAttribute
    public void setFormat(DiscoveryFormat format) {
        this.format = format;
    }

    public String getRootDomain() {
        return rootDomain;
    }

    /**
     * @description Domain the identifiers in the document are built from. An entry is identified as
     * <code>&lt;rootDomain&gt;:&lt;id&gt;</code>, where the id comes from its OpenAPI document or a
     * slug of its host, port and path. An api that sets <code>id</code> is identified by that value
     * instead, without the domain, and by <code>&lt;id&gt;:&lt;document id&gt;</code> when it
     * carries several OpenAPI documents.
     * @default membrane
     * @example example.com
     */
    @MCAttribute
    public void setRootDomain(String rootDomain) {
        this.rootDomain = rootDomain;
    }

    public String getCollectionId() {
        return collectionId;
    }

    /**
     * @description Identifier of the collection itself, forming the second half of its
     * <code>&lt;rootDomain&gt;:&lt;collectionId&gt;</code> identifier.
     * @default apis
     * @example gateway
     */
    @MCAttribute
    public void setCollectionId(String collectionId) {
        this.collectionId = collectionId;
    }

    public String getCollectionName() {
        return collectionName;
    }

    /**
     * @description Human readable name of the collection.
     * @default APIs
     * @example Membrane Gateway APIs
     */
    @MCAttribute(attributeName = "name")
    public void setCollectionName(String collectionName) {
        this.collectionName = collectionName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * @description Human readable description of the collection.
     * @default APIs served by this gateway
     * @example Every API of the payments platform
     */
    @MCAttribute
    public void setDescription(String description) {
        this.description = description;
    }

    public String getUrl() {
        return url;
    }

    /**
     * @description URL the document itself is published under. Derived from each incoming request
     * when omitted, which is correct unless a reverse proxy in front of the gateway rewrites host
     * or path.
     * @default derived from the request
     * @example https://example.com/.well-known/api-catalog
     */
    @MCAttribute
    public void setUrl(String url) {
        this.url = url;
    }

    public LocalDate getCreated() {
        return created;
    }

    /**
     * @description Day the collection was created, as an ISO date.
     * @default the day Membrane started
     * @example 2026-01-15
     */
    @MCAttribute
    public void setCreated(String created) {
        this.created = parseDate("created", created);
    }

    public LocalDate getModified() {
        return modified;
    }

    /**
     * @description Day the collection last changed, as an ISO date.
     * @default the day Membrane started
     * @example 2026-01-15
     */
    @MCAttribute
    public void setModified(String modified) {
        this.modified = parseDate("modified", modified);
    }

    private static LocalDate parseDate(String attribute, String value) {
        try {
            return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException e) {
            throw new ConfigurationException(
                    "Cannot parse apiDiscovery/@%s \"%s\". Expected an ISO date such as 2026-01-15."
                            .formatted(attribute, value), e);
        }
    }

    @Override
    public String getDisplayName() {
        return "API Discovery";
    }

    @Override
    public String getShortDescription() {
        return "Publishes all deployed APIs as a discovery document.";
    }
}
