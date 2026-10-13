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
import org.slf4j.*;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;

import java.time.*;
import java.util.*;

import static com.predic8.membrane.core.http.Header.*;

/**
 * @description Publishes every API deployed on this gateway in Membrane's own catalog format,
 * which carries everything the gateway knows about an API, including the method, path and URL of
 * every operation. The catalog is collected from the running configuration on each request and
 * therefore never goes stale.
 * <p>
 * An API described by an OpenAPI document is listed with the title, description and version from
 * that document, plus links to the document itself and to its Swagger UI. A SOAP proxy is listed
 * with a link to the WSDL the gateway publishes. Any other proxy is listed with its own name and
 * URL. Internal proxies and proxies whose initialisation failed are left out.
 * </p>
 * <p>
 * A browser, or any client whose <code>Accept</code> header prefers HTML, gets an HTML page of
 * the same catalog instead, operations included, with links to the Swagger UI of every API.
 * </p>
 * <p>
 * Answers every request that reaches it and ends the flow, so give it an api of its own. RFC 9727
 * reserves <code>/.well-known/api-catalog</code> for its own format, so that path is refused here;
 * use <code>apiCatalog</code> there.
 * </p>
 * <pre>
 * apiDiscovery:
 *   [ baseUrl: &lt;url&gt; ]
 *   [ collectionId: &lt;id&gt; ]
 *   [ name: &lt;name&gt; ]
 *   [ description: &lt;text&gt; ]
 *   [ created: &lt;yyyy-mm-dd&gt; ]
 *   [ modified: &lt;yyyy-mm-dd&gt; ]
 * </pre>
 * @topic 5. OpenAPI
 * @yaml <pre><code>
 * api:
 *   port: 2000
 *   path:
 *     uri: /catalog
 *   flow:
 *     - apiDiscovery:
 *         collectionId: gateway
 *         name: Example APIs
 * </code></pre>
 */
@MCElement(name = "apiDiscovery")
public class ApiDiscoveryInterceptor extends AbstractApiDiscoveryInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ApiDiscoveryInterceptor.class);

    private static final String HTML_CONTENT_SECURITY_POLICY = "default-src 'none'; style-src 'unsafe-inline'";

    private String collectionId = "apis";
    private String collectionName = "APIs";
    private String description = "APIs served by this gateway";
    private LocalDate created = LocalDate.now();
    private LocalDate modified = LocalDate.now();

    private CatalogRenderer htmlRenderer;

    @Override
    public void init() {
        super.init();
        htmlRenderer = new HtmlCatalogRenderer(collection());
    }

    @Override
    protected CatalogRenderer createRenderer() {
        return new MembraneCatalogRenderer(collection());
    }

    @Override
    protected CatalogRenderer negotiate(Exchange exc, CatalogRenderer renderer) {
        return acceptsHtml(exc) ? htmlRenderer : renderer;
    }

    @Override
    protected void addHeaders(Response.ResponseBuilder response, Catalog catalog, CatalogRenderer negotiated) {
        // The answer is HTML or JSON depending on Accept, so caches must key on it.
        response.header(VARY, ACCEPT);
        // The page shows text from OpenAPI documents and needs no script, so it allows none.
        if (negotiated == htmlRenderer)
            response.header(CONTENT_SECURITY_POLICY, HTML_CONTENT_SECURITY_POLICY);
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

    public String getCollectionId() {
        return collectionId;
    }

    /**
     * @description Identifier of the collection itself, forming the second half of its
     * <code>&lt;domain&gt;:&lt;collectionId&gt;</code> identifier.
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

    private CollectionInfo collection() {
        return new CollectionInfo(collectionId, collectionName, description, created, modified);
    }

    @Override
    public String getDisplayName() {
        return "API Discovery";
    }

    @Override
    public String getShortDescription() {
        return "Publishes all deployed APIs in Membrane's catalog format, as JSON or HTML.";
    }
}
