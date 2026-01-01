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
package com.predic8.membrane.core.interceptor.server;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.resolver.*;
import com.predic8.membrane.core.util.URI;
import org.apache.commons.lang3.*;
import org.jetbrains.annotations.*;
import org.slf4j.*;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.http.Response.ok;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.resolver.ResolverMap.combine;
import static com.predic8.membrane.core.util.HttpUtil.*;
import static com.predic8.membrane.core.util.WebServerUtil.getContentType;
import static com.predic8.membrane.core.util.text.TextUtil.*;
import static java.lang.System.currentTimeMillis;

/**
 * @description Serves static files based on the request's path.
 * @explanation <p>
 * Note that <i>docBase</i> any <i>location</i>: A relative or absolute directory, a
 * "classpath://com.predic8.membrane.core.interceptor.administration.docBase" expression or a URL.
 * </p>
 * <p>
 * The interceptor chain will not continue beyond this interceptor, as it either successfully returns a
 * HTTP response with the contents of a file, or a "404 Not Found." error.
 * </p>
 * @topic 6. Misc
 */
@MCElement(name = "webServer")
public class WebServerInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WebServerInterceptor.class.getName());

    private static final String[] EMPTY = new String[0];

    private String docBase = "docBase";
    private boolean docBaseIsNormalized = false;
    private String[] index = EMPTY;
    private boolean generateIndex;

    public WebServerInterceptor() {
        name = "web server";
    }

    public WebServerInterceptor(Router r) {
        name = "web server";
        this.router = r;
    }

    @Override
    public void init() {
        super.init();
        normalizeDocBase();
    }

    private void normalizeDocBase() {
        // deferred init of docBase because router is needed
        docBase = docBase.replaceAll(Pattern.quote("\\"), "/");
        if (docBaseIsNormalized) return;
        if (!docBase.endsWith("/"))
            docBase += "/";
        try {
            this.docBase = getAbsolutePathWithSchemePrefix(docBase);
        } catch (Exception e) {
            log.error("While handling docBase={}", this.docBase, e);
        }
        docBaseIsNormalized = true;
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        try {
            return handleRequestInternal(exc);
        } catch (IOException e) {
            log.error("", e);
            internal(router.getConfiguration().isProduction(), getDisplayName())
                    .flow(Flow.REQUEST)
                    .detail("Error serving document")
                    .exception(e)
                    .buildAndSetResponse(exc);
            return ABORT;
        }
    }

    private @NotNull Outcome handleRequestInternal(Exchange exc) throws IOException {
        normalizeDocBase();

        String uri = getUri(exc);
        if (uri == null) return ABORT;

        if (generateIndex) {
            if (router.getResolverMap().getChildren(combine(router.getBaseLocation(), docBase, uri)) != null) {
                if (tryToReceiveResource(exc, uri))
                    return RETURN;
                if (tryToReceiveResource(exc, uri + "/"))
                    return RETURN;
                Outcome outcome = generateHtmlResponseFromChildren(exc, uri);
                if (outcome != null)
                    return outcome;
            }
        }

        try {
            exc.setTimeReqSent(currentTimeMillis());
            exc.setResponse(createResponse(router.getResolverMap(), combine(router.getBaseLocation(), docBase, uri)));
            exc.setReceived();
            exc.setTimeResReceived(currentTimeMillis());
            return RETURN;
        } catch (Exception e) {
            if (tryToReceiveResource(exc, uri))
                return RETURN;
            if (tryToReceiveResource(exc, uri + "/"))
                return RETURN;
        }

        if (generateIndex) {
            Outcome outcome = generateHtmlResponseFromChildren(exc, uri);
            if (outcome != null) {
                return outcome;
            }
        }

        exc.setResponse(Response.notFound().build());
        return ABORT;
    }

    private @Nullable String getUri(Exchange exc) {
        URI requestUri = getRequestUri(exc);
        if (requestUri == null)
            return null;

        String path = requestUri.getPath();
        if (path == null) {
            exc.setResponse(Response.badRequest().body("").build());
            return null;
        }
        log.debug("request: {}", path);

        String encodedPath = path.replace(" ", "%20");
        try {
            if (escapesPath(encodedPath) || escapesPath(router.getUriFactory().create(encodedPath).getPath())) {
                exc.setResponse(Response.badRequest().body("").build());
                return null;
            }
        } catch (URISyntaxException e) {
            exc.setResponse(Response.badRequest().body("").build());
            return null;
        }
        return encodedPath.startsWith("/") ? encodedPath.substring(1) : encodedPath;
    }


    private @Nullable URI getRequestUri(Exchange exc) {
        try {
            return router.getUriFactory().create(exc.getDestinations().getFirst());
        } catch (URISyntaxException e) {
            internal(router.getConfiguration().isProduction(), getDisplayName())
                    .addSubSee("uri-creation")
                    .detail("Could not create uri")
                    .exception(e)
                    .buildAndSetResponse(exc);
            return null;
        }
    }

    private boolean tryToReceiveResource(Exchange exc, String uri) {
        for (String i : index) {
            Response response = createResponse(
                    router.getResolverMap(),
                    combine(router.getBaseLocation(), docBase, uri + i)
            );

            if (!response.isOk())
                continue;

            exc.setResponse(response);
            exc.setReceived();
            exc.setTimeResReceived(currentTimeMillis());
            return true;
        }
        return false;
    }

    private Outcome generateHtmlResponseFromChildren(Exchange exc, String uri) throws FileNotFoundException {
        List<String> children = router.getResolverMap().getChildren(combine(router.getBaseLocation(), docBase, uri));
        if (children == null) {
            return null;
        }
        Collections.sort(children);
        exc.setResponse(ok().contentType(TEXT_HTML).body(generateHtmlContent(children, determineBaseUri(exc, uri))).build());
        return RETURN;
    }

    private String determineBaseUri(Exchange exc, String uri) {
        if (uri.endsWith("/")) {
            return "";
        }

        String base = exc.getRequestURI();
        int lastSlashPos = base.lastIndexOf('/');
        if (lastSlashPos != -1) {
            base = base.substring(lastSlashPos + 1);
        }
        return (base.isEmpty() ? "." : base) + "/";
    }

    private String generateHtmlContent(List<String> children, String baseUri) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body><tt>");
        for (String child : children) {
            sb.append(String.format("<a href=\"%s%s\">%s</a><br/>", baseUri, child, child));
        }
        sb.append("</tt></body></html>");
        return sb.toString();
    }

    private boolean escapesPath(String uri) {
        return uri.endsWith("..") || uri.endsWith("../") || uri.endsWith("..\\") //
                || uri.contains("/../") || uri.contains("..\\") || uri.contains(":/") || uri.contains("file:\\") //
                || uri.startsWith("..");
    }

    public Response createResponse(ResolverMap rr, String resPath) {
        try {
            return ok()
                    .header(createHeaders(getContentType(resPath)))
                    .body(rr.resolve(resPath), true)
                    .build();
        } catch (Exception e) {
            return internal(router.getConfiguration().isProduction(), getDisplayName())
                    .title("Could not resolve file")
                    .topLevel("path", resPath)
                    .exception(e)
                    .build();
        }
    }

    public String getDocBase() {
        return docBase;
    }

    /**
     * @description Sets path to the directory that contains the web content.
     * @default docBase
     * @example docBase
     */
    @Required
    @MCAttribute
    public void setDocBase(String docBase) {
        this.docBase = docBase;
        docBaseIsNormalized = false;
    }

    private String getAbsolutePathWithSchemePrefix(String path) {
        try {
            Path p = Paths.get(path);
            if (p.isAbsolute())
                return p.toUri().toString();
        } catch (Exception ignored) {
        }
        return getNewPath(path);
    }

    private @NotNull String getNewPath(String path) {
        String newPath = combine(router.getBaseLocation(), path);
        if (newPath.endsWith(File.separator + File.separator))
            newPath = newPath.substring(0, newPath.length() - 1);
        if (!newPath.endsWith("/"))
            return newPath + "/";
        return newPath;
    }

    public String getIndex() {
        return StringUtils.join(index, ",");
    }

    @MCAttribute
    public void setIndex(String i) {
        if (i == null)
            index = EMPTY;
        else
            index = i.split(",");
    }

    @SuppressWarnings("unused")
    public boolean isGenerateIndex() {
        return generateIndex;
    }

    @MCAttribute
    public void setGenerateIndex(boolean generateIndex) {
        this.generateIndex = generateIndex;
    }

    @Override
    public String getShortDescription() {
        return "Serves static files from<br/>" + linkURL(docBase) + " .";
    }

}
