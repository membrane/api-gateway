/* Copyright 2011, 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.rewrite;

import com.googlecode.jatl.Html;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.Required;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.proxies.AbstractServiceProxy;
import com.predic8.membrane.core.util.URIFactory;
import com.predic8.membrane.core.util.URLUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringWriter;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.ListIterator;
import java.util.regex.Pattern;

import static com.predic8.membrane.core.exceptions.ProblemDetails.user;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.Set.REQUEST_FLOW;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static com.predic8.membrane.core.interceptor.rewrite.RewriteInterceptor.Type.*;
import static com.predic8.membrane.core.util.URIFactory.ALLOW_ILLEGAL_CHARACTERS_URI_FACTORY;
import static com.predic8.membrane.core.util.text.TextUtil.capitalizeFirstCharacter;
import static com.predic8.membrane.core.util.text.TextUtil.toEnglishList;
import static java.util.Locale.US;

/**
 * @description <p>Rewrites the path and query of requests, or answers them with a redirect. Acts on requests only.</p>
 * <p>The mappings are checked in order against the path and query, and the first one that matches is applied.
 * Requests that match no mapping pass unchanged.</p>
 * <p>A rewrite does not change the call to a target URL with a path such as <code>https://api.example.com/orders</code>:
 * that URL is sent as it is. Redirects work with any target.</p>
 * <p>See tutorials/routing/10-Rewrite-and-Redirect.yaml.</p>
 * <h3>Security Hint</h3>
 * <p>The expressions run on the request path. Overlapping quantifiers such as <code>(.*)/(.*)</code> can take
 * a long time on crafted paths. See
 * <a href="https://www.membrane-api.io/security/membrane-redos-protection.html">ReDoS Protection</a>.</p>
 * @yaml <pre><code>
 * api:
 *   port: 2000
 *   flow:
 *     - rewriter:
 *         - from: ^/store/(.*)
 *           to: /shop/v2/$1
 *         - from: ^/v1/(.*)
 *           to: /store/$1
 *           do: redirect_permanent
 *   target:
 *     url: https://api.predic8.de
 * </code></pre>
 * @topic 9. Misc
 */
@MCElement(name = "rewriter", noEnvelope = true, component = false)
public class RewriteInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RewriteInterceptor.class.getName());

    public static final String URL_NOT_PROCESSABLE = "The URL may be invalid, or the server could not process it.";

    /**
     * Scheme followed by "://" at the start (RFC 3986 section 3.1). A "://" later on, e.g. in the query, does not count.
     */
    private static final Pattern ABSOLUTE_URL = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*://");

    public enum Type {
        REWRITE,
        REDIRECT_TEMPORARY,
        REDIRECT_PERMANENT,
    }

    @MCElement(name = "map", component = false, id = "rewriter-map")
    public static class Mapping {
        public String to;
        public String from;
        private Type do_;

        private Pattern pattern;

        public Mapping() {
        }

        public Mapping(String from, String to, String do_) {
            this.from = from;
            this.to = to;
            this.do_= parseDo(do_);
            pattern = Pattern.compile(from);
        }

        private Type parseDo(String do_) {
            if (do_ == null || do_.isEmpty())
                return null;

            String what = do_.toLowerCase(US);
            return switch (what) {
                case "rewrite" -> REWRITE;
                case "redirect", "redirect-temporary" -> REDIRECT_TEMPORARY;
                case "redirect-permanent" -> REDIRECT_PERMANENT;
                default -> throw new IllegalArgumentException("Unknown value '%s' for rewriter/@do.".formatted(do_));
            };
        }

        public boolean matches(String uri) {
            return pattern.matcher(uri).find();
        }

        public String getFrom() {
            return from;
        }

        /**
         * @description Java regular expression searched in the path and query of the request. It is not anchored:
         * use <code>^</code> to match from the start of the path.
         * @example ^/store/(.*)
         */
        @Required
        @MCAttribute
        public void setFrom(String from) {
            this.from = from;
            if (from == null)
                pattern = null;
            else
                pattern = Pattern.compile(from);
        }

        public String getTo() {
            return to;
        }

        /**
         * @description Replacement for the matched part. <code>$1</code>, <code>$2</code>, ... insert the groups
         * captured by <code>from</code>. An absolute URL such as <code>https://example.com/$1</code> redirects by
         * default; with <code>do: rewrite</code> it replaces the backend instead, if the target URL has no path or
         * only <code>/</code>. A target URL with a longer path is always called as configured.
         * @example /shop/v2/$1
         */
        @Required
        @MCAttribute
        public void setTo(String to) {
            this.to = to;
        }

        public Type getDo() {
            if (do_ == null)
                return isAbsoluteUrl(to) ? REDIRECT_TEMPORARY : REWRITE;
            return do_;
        }

        /**
         * @description <code>rewrite</code> changes the request and passes it on. <code>redirect_temporary</code>
         * (307) and <code>redirect_permanent</code> (301) answer with the new location without calling the backend.
         * @default rewrite, or redirect_temporary if <code>to</code> is an absolute URL
         * @example redirect_permanent
         */
        @MCAttribute
        public void setDo(Type do_) {
            this.do_ = do_;
        }
    }

    private List<Mapping> mappings = new ArrayList<>();

    public RewriteInterceptor() {
        name = "url rewriter";
        setAppliedFlow(REQUEST_FLOW);
    }

    @Override
    public Outcome handleRequest(Exchange exc) {

        logMappings();

        // The URI factory escapes e.g. a backslash, which a browser would turn into a host in '/\evil.example'.
        // A URI parser takes a leading "//" as the start of an authority, so "//x/old" would lose "x".
        // The request target is a path, so it is parsed behind a dummy authority.
        final var requestUri = exc.getRequest().getUri();
        final var requestPathQuery = getPathQueryOrSetError(router.getConfiguration().getUriFactory(),
                requestUri.startsWith("//") ? "http://dummy" + requestUri : requestUri, exc);
        if (requestPathQuery == null)
            return RETURN;
        final var mapping = findFirstMatchingRegEx(requestPathQuery);

        // A redirect is answered to the client, so it is decided on the request and not on the destination. A target
        // URL with a base path is the destination of every request and must not end up in the Location header.
        if (mapping != null && mapping.getDo() != REWRITE) {
            log.debug("match found: {} redirecting to: {} for type: {}", mapping.from, mapping.to, mapping.getDo());
            exc.setResponse(Response.redirect(replace(requestPathQuery, mapping), mapping.getDo() == REDIRECT_PERMANENT).build());
            return RETURN;
        }

        if (!isTargetUrlUsedAsIs(exc) && !rewriteDestinations(exc))
            return RETURN;

        if (mapping != null) {
            var newUri = replace(requestPathQuery, mapping);
            if (isAbsoluteUrl(newUri)) {
                newUri = getPathQueryOrSetError(router.getConfiguration().getUriFactory(), newUri, exc);
                if (newUri == null)
                    return RETURN;
            }
            exc.getRequest().setUri(newUri);
        }

        return CONTINUE;
    }

    /**
     * @return false if a destination could not be parsed, the error response is set then
     */
    private boolean rewriteDestinations(Exchange exc) {
        final var destinationUriFactory = getDestinationUriFactory(exc);
        ListIterator<String> it = exc.getDestinations().listIterator();
        while (it.hasNext()) {
            String dest = it.next();
            String pathQuery = getPathQueryOrSetError(destinationUriFactory, dest, exc);
            if (pathQuery == null)
                return false;

            int pathBegin = -1;
            // a relative dest has no authority, a '//' in its query must not be taken for one
            int authorityBegin = isAbsoluteUrl(dest) ? dest.indexOf("//") : -1;
            if (authorityBegin != -1)
                pathBegin = dest.indexOf("/", authorityBegin + 2);
            String schemaHostPort = pathBegin == -1 ? null : dest.substring(0, pathBegin);

            log.debug("pathQuery: {}", pathQuery);
            log.debug("schemaHostPort: {}", schemaHostPort);

            Mapping mapping = findFirstMatchingRegEx(pathQuery);
            if (mapping == null || mapping.getDo() != REWRITE)
                continue;

            log.debug("match found: {} replacing with: {}", mapping.from, mapping.to);

            String newDest = replace(pathQuery, mapping);

            if (!isAbsoluteUrl(newDest) && schemaHostPort != null) {
                // prepend schema, host and port from original uri
                newDest = schemaHostPort + newDest;
            }

            it.set(newDest);
        }
        return true;
    }

    /**
     * A target URL with a path is used as it is, it is already the rewriting the user configured. That includes any
     * destination replacing it, e.g. set by a balancer.
     */
    private static boolean isTargetUrlUsedAsIs(Exchange exc) {
        return exc.getProxy() instanceof AbstractServiceProxy asp && asp.getTarget().isUrlUsedAsIs();
    }

    private static boolean isAbsoluteUrl(String url) {
        return ABSOLUTE_URL.matcher(url).find();
    }

    /**
     * A target URL template without a path, e.g. http://${header.region}.backend, reaches the destinations
     * unevaluated (see DispatchingInterceptor), so its '{' and '}' must not fail the parsing. The target only allows
     * templates with a strict URI factory, which already parsed any request path that is part of a destination.
     */
    private URIFactory getDestinationUriFactory(Exchange exc) {
        if (exc.getProxy() instanceof AbstractServiceProxy asp && asp.getTarget().isUrlIsTemplate())
            return ALLOW_ILLEGAL_CHARACTERS_URI_FACTORY;
        return router.getConfiguration().getUriFactory();
    }

    private String getPathQueryOrSetError(URIFactory factory, String destination, Exchange exc) {
        try {
            return URLUtil.getPathQuery(factory, destination);
        } catch (URISyntaxException | IllegalArgumentException e) {
            log.info("Can't parse query: {}", destination);
            user(router.getConfiguration().isProduction(), getDisplayName())
                    .addSubType("path")
                    .title("Request URL could not be processed.")
                    .detail(URL_NOT_PROCESSABLE, true)
                    .internal("component", "rewrite")
                    .internal("path", destination)
                    .internal("reason", e.getMessage())
                    .buildAndSetResponse(exc);
            return null;
        }
    }

    private void logMappings() {
        for (Mapping m : mappings) {
            log.debug("[from: {}],[to: {}],[do: {}]", m.from, m.to, m.getDo());
        }
    }

    private String replace(String uri, Mapping mapping) {
        String replaced = uri.replaceAll(mapping.from, mapping.to);
        log.debug("replaced URI: {}", replaced);
        return replaced;
    }

    private Mapping findFirstMatchingRegEx(String uri) {
        for (Mapping m : mappings) {
            if (m.matches(uri))
                return m;
        }
        return null;
    }

    public List<Mapping> getMappings() {
        return mappings;
    }

    /**
     * @description Mappings from a regular expression to a replacement, checked in order.
     */
    @Required
    @MCChildElement
    public void setMappings(List<Mapping> mappings) {
        this.mappings = mappings;
    }

    @Override
    public String getShortDescription() {
        EnumSet<Type> s = EnumSet.noneOf(Type.class);
        for (Mapping m : mappings)
            s.add(m.getDo());

        return capitalizeFirstCharacter(toEnglishList("or",
                s.contains(REDIRECT_PERMANENT) || s.contains(REDIRECT_TEMPORARY) ?
                        toEnglishList("or",
                                s.contains(REDIRECT_PERMANENT) ? "permanently" : null,
                                s.contains(REDIRECT_TEMPORARY) ? "temporarily" : null) +
                        " redirects" : null,
                s.contains(REWRITE) ? "rewrites" : null)) +
               " URLs.";
    }

    @Override
    public String getLongDescription() {
        StringWriter sw = new StringWriter();
        new Html(sw) {{
            text(getShortDescription());

            table().style("margin-top: 5pt;");
            thead();
            tr();
            th().text("From").end();
            th().text("To").end();
            th().text("Action").end();
            end();
            end();
            tbody();
            for (Mapping m : mappings) {
                tr();
                td().text(m.from).end();
                td().text(m.to).end();
                td().text(m.getDo().toString()).end();
                end();
            }
            end();
            end();
        }};
        return sw.toString();
    }
}