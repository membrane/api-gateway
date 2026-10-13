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
import java.time.*;
import java.time.format.*;
import java.util.*;

import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
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

    /**
     * Well-known URI RFC 9727 reserves for the api-catalog document.
     */
    static final String WELL_KNOWN_PATH = "/.well-known/api-catalog";

    private String baseUrl;

    private CatalogRenderer renderer;
    private CatalogCollector collector;

    @Override
    public void init() {
        super.init();
        renderer = createRenderer();
        collector = new CatalogCollector(baseUrl, renderer.rendersOperations());
        // The proxy is unknown when the plugin sits in the else branch of an if.
        if (getProxy() != null && getProxy().getKey() != null)
            checkPath(getProxy().getKey().getPath());
    }

    /**
     * Renderer of the format this interceptor publishes. Called once, from {@link #init()}, after
     * the configuration is complete.
     */
    protected abstract CatalogRenderer createRenderer();

    /**
     * RFC 9727 makes the api-catalog Linkset mandatory at the well-known URI, and a client reading
     * any other format there misreads it. Only the RFC 9727 format may therefore be served there.
     *
     * @param path path of the api this interceptor serves, or null when it serves every path
     */
    protected void checkPath(String path) {
        if (WELL_KNOWN_PATH.equals(path))
            throw new ConfigurationException(
                    "%s cannot be served under %s. RFC 9727 requires an api-catalog Linkset there, so use apiCatalog or move the api to another path."
                            .formatted(elementName(), WELL_KNOWN_PATH));
    }

    @Override
    public final Outcome handleRequest(Exchange exc) {
        try {
            final var catalog = collector.collect(router, exc);
            final var negotiated = negotiate(exc, renderer);
            final var response = ok().contentType(negotiated.contentType());
            addHeaders(response, catalog, negotiated);
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
     * Renderer that answers this request. Only a format with more than one representation picks
     * anything but its own renderer.
     */
    protected CatalogRenderer negotiate(Exchange exc, CatalogRenderer renderer) {
        return renderer;
    }

    /**
     * Headers the format adds to its response, on top of <code>Content-Type</code>.
     */
    protected void addHeaders(Response.ResponseBuilder response, Catalog catalog, CatalogRenderer negotiated) {
    }

    /**
     * Name of the configuration element, for messages that tell the operator what to change.
     */
    protected String elementName() {
        final var element = getClass().getAnnotation(MCElement.class);
        return element == null ? getClass().getSimpleName() : element.name();
    }

    /**
     * Parses a configured ISO date, for the formats that carry one.
     */
    protected LocalDate parseDate(String attribute, String value) {
        try {
            return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException e) {
            throw new ConfigurationException(
                    "Cannot parse %s/@%s \"%s\". Expected an ISO date such as 2026-01-15."
                            .formatted(elementName(), attribute, value), e);
        }
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * @description Absolute URL clients reach the gateway under, such as
     * <code>https://api.example.com</code>. It is the origin of the catalog URL and of every api
     * bound to all hosts, and its host is the domain identifiers in the document are built from.
     * Without it, all of that comes from the <code>Host</code> header of each request, which is
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
