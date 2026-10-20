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

import java.net.URI;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.Response.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;

/**
 * Publishes every API deployed on this gateway as a catalog in one discovery format. Collecting
 * the catalog, answering <code>HEAD</code> and guarding the RFC 9727 well-known path are the same
 * for every format; a subclass contributes its renderer and, where its format needs them, extra
 * response headers and configuration.
 * <p>
 * Answers every request that reaches it and ends the flow.
 */
public abstract class AbstractApiDiscoveryInterceptor extends AbstractInterceptor {

    private String baseUrl;
    private List<TagRule> tags = List.of();

    private CatalogRenderer renderer;
    private CatalogCollector collector;

    @Override
    public void init() {
        super.init();
        renderer = createRenderer();
        collector = new CatalogCollector(baseUrl, tags);
        // The proxy is unknown when the plugin sits in the else branch of an if.
    }

    protected abstract CatalogRenderer createRenderer();

    @Override
    public final Outcome handleRequest(Exchange exc) {
        try {
            final var catalog = collector.collect(router, exc);
            final var negotiated = negotiate(exc, renderer);
            final var document = negotiated.render(catalog);
            final var etag = etag(document);
            final var response = ok().contentType(negotiated.contentType()).header(ETAG, etag);
            addHeaders(response, catalog, negotiated);
            final var head = exc.getRequest().isHEADRequest();
            final var notModified = matchesAny(exc.getRequest().getHeader().getFirstValue(IF_NONE_MATCH), etag);
            if (notModified)
                response.status(304).bodyEmpty();
            // RFC 9727 requires HEAD on the catalog to answer with the headers but no document.
            else if (head)
                response.bodyEmpty();
            else
                response.body(document);
            final var built = response.build();
            // RFC 9110 §8.6: without a body, HEAD and 304 announce the length a GET would get.
            if (notModified || head)
                built.getHeader().setContentLength(document.length);
            exc.setResponse(built);
            return RETURN;
        } catch (Exception e) {
            internal(router.getConfiguration().isProduction(), getDisplayName())
                    .detail("Could not render the API discovery document!")
                    .exception(e)
                    .buildAndSetResponse(exc);
            return ABORT;
        }
    }

    private static String etag(byte[] document) throws NoSuchAlgorithmException {
        final var hash = MessageDigest.getInstance("SHA-256").digest(document);
        return "\"" + HexFormat.of().formatHex(hash, 0, 16) + "\"";
    }

    private static boolean matchesAny(String ifNoneMatch, String etag) {
        if (ifNoneMatch == null) return false;
        return Arrays.stream(ifNoneMatch.split(","))
                .map(String::strip)
                .map(tag -> tag.startsWith("W/") ? tag.substring(2) : tag)
                .anyMatch(tag -> tag.equals("*") || tag.equals(etag));
    }

    protected CatalogRenderer negotiate(Exchange exc, CatalogRenderer renderer) {
        return renderer;
    }

    String catalogType() {
        return renderer.contentType();
    }

    protected void addHeaders(Response.ResponseBuilder response, Catalog catalog, CatalogRenderer negotiated) {
    }

    protected String elementName() {
        final var element = getClass().getAnnotation(MCElement.class);
        return element == null ? getClass().getSimpleName() : element.name();
    }

    public List<TagRule> getTags() {
        return tags;
    }

    /**
     * @description Selects the apis to publish by their tags. Rules apply in order: the first one
     * that matches a tag of the api decides whether it is listed, and an api no rule matches is left
     * out. Without rules every api is listed. A rule's value is a regular expression that must match
     * a whole tag, so a plain name matches just that tag.
     */
    @MCChildElement
    public void setTags(List<TagRule> tags) {
        this.tags = List.copyOf(tags);
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * @description Absolute URL clients reach the gateway under, such as
     * <code>https://api.example.com</code>. It is the origin of the catalog URL and of every api
     * bound to all hosts, and in APIs.json its host is the domain of every identifier. Without it,
     * all of that comes from the <code>Host</code> header of each request, which is
     * correct unless a reverse proxy in front of the gateway changes the origin. An api bound to
     * one host keeps that host either way.
     * @default derived from the Host header of the request
     * @example https://api.example.com
     */
    @MCAttribute
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = validBaseUrl(baseUrl);
    }

    private String validBaseUrl(String baseUrl) {
        try {
            final var uri = new URI(baseUrl);
            final var scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if ((scheme.equals("http") || scheme.equals("https")) && uri.getHost() != null
                && uri.getQuery() == null && uri.getFragment() == null)
                return baseUrl;
        } catch (URISyntaxException ignored) {
            // Reported below together with every other malformed value.
        }
        throw new ConfigurationException(
                "Cannot use %s/@baseUrl \"%s\". Expected an absolute http or https URL without query or fragment, such as https://api.example.com."
                        .formatted(elementName(), baseUrl));
    }
}
