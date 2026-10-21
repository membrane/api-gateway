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
import com.predic8.membrane.core.http.*;
import org.slf4j.*;

import static com.predic8.membrane.core.http.Header.*;

/**
 * @description Publishes every API deployed on this gateway as an RFC 9727 api-catalog, a Linkset
 * served as <code>application/linkset+json</code>. RFC 9727 clients look for it under the
 * well-known path <code>/.well-known/api-catalog</code>, so give the surrounding api that path.
 * The catalog is collected from the running configuration on each request and therefore never
 * goes stale.
 * <p>
 * The catalog bookmarks every API with an <code>item</code> link, titled by its name and, where an
 * OpenAPI document gives one, its version. An api that publishes a catalog itself, such as one with
 * <code>apisJson</code>, is linked with <code>api-catalog</code> instead. An API described by an OpenAPI
 * document also links that document as <code>service-desc</code> and its Swagger UI as
 * <code>service-doc</code>; a SOAP proxy links the WSDL the gateway publishes as
 * <code>service-desc</code>. Internal proxies and proxies whose initialisation failed are left
 * out. Every response, <code>HEAD</code> included, advertises the catalog with a
 * <code>Link</code> header of relation <code>api-catalog</code>.
 * </p>
 * <p>
 * Answers every request that reaches it and ends the flow, so give it an api of its own.
 * </p>
 * <pre>
 * apiCatalog:
 *   [ baseUrl: &lt;url&gt; ]
 *   [ tags:
 *       - include | exclude: &lt;tag&gt; ]
 * </pre>
 * @topic 5. OpenAPI
 * @yaml <pre><code>
 * api:
 *   port: 2000
 *   path:
 *     uri: /.well-known/api-catalog
 *   flow:
 *     - apiCatalog:
 *         baseUrl: https://api.example.com
 * </code></pre>
 */
@MCElement(name = "apiCatalog")
public class ApiCatalogInterceptor extends AbstractApiDiscoveryInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ApiCatalogInterceptor.class);

    @Override
    protected CatalogRenderer createRenderer() {
        return new ApiCatalogRenderer();
    }

    /**
     * A Linkset served anywhere but the well-known URI will not be found by a client that follows
     * RFC 9727. That is allowed, as the RFC lets a publisher host the document anywhere, but rarely
     * intended.
     */
    @Override
    protected void checkPath(String path) {
        if (!WELL_KNOWN_PATH.equals(path))
            log.warn("apiCatalog publishes an RFC 9727 api-catalog under the path {}, but RFC 9727 "
                     + "clients look for it at {}. Set that path on the surrounding api.",
                    path == null ? "/" : path, WELL_KNOWN_PATH);
    }

    /**
     * RFC 9727 requires the response to advertise the catalog with the api-catalog relation, whose
     * target must be a Linkset, which is why only this format sends it.
     */
    @Override
    protected void addHeaders(Response.ResponseBuilder response, Catalog catalog, CatalogRenderer negotiated) {
        if (catalog.url() != null)
            response.header(LINK, "<%s>; rel=\"api-catalog\"".formatted(catalog.url()));
    }

    @Override
    public String getDisplayName() {
        return "API Catalog";
    }

    @Override
    public String getShortDescription() {
        return "Publishes all deployed APIs as an RFC 9727 api-catalog.";
    }
}
