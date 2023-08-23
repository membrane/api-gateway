/*
 *  Copyright 2023 predic8 GmbH, www.predic8.com
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.openapi.serviceproxy;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.openapi.util.*;
import com.predic8.membrane.core.util.*;
import org.slf4j.*;

import java.io.*;
import java.net.*;

import static java.util.Objects.*;

/**
 * @description
 */
@MCElement(name = "rewrite", topLevel = false)
public class Rewrite {

    private static final Logger log = LoggerFactory.getLogger(Rewrite.class.getName());

    Integer port;
    String protocol;
    String host;

    public JsonNode rewrite(OpenAPIRecord rec, Exchange exc, URIFactory uriFactory) throws URISyntaxException, IOException {
        if (rec.isVersion3()) {
            return rewriteOpenAPI3(exc, uriFactory, rec.node);
        }

        return rewriteSwagger2(exc, rec.node);
    }

    private JsonNode rewriteOpenAPI3(Exchange exc, URIFactory uriFactory, JsonNode node) throws URISyntaxException {
        // Expensive cloning before null check is fine cause most of OpenAPIs will have servers.
        // Caching should not be needed cause the OpenAPI is not so often retrieved. Maybe practice
        // will prove that wrong.
        JsonNode rewritten = node.deepCopy();
        for (JsonNode server : rewritten.get("servers")) {
            rewriteServerEntry(exc, uriFactory, server);
        }
        return rewritten;
    }

    private void rewriteServerEntry(Exchange exc, URIFactory uriFactory, JsonNode server) throws URISyntaxException {
        String url = server.get("url").asText();
        String rewritten = rewriteUrl(exc, url, uriFactory);
        ((ObjectNode) server).put("url", rewritten);
        log.debug("Rewriting {} to {}", url, rewritten);
    }

    /**
     * Rewrites URL from <b>OpenAPI 3.X</b>
     *
     * @param exc        Exchange
     * @param url        URL to rewrite
     * @param uriFactory URIFactory
     * @return Rewritten URL
     * @throws URISyntaxException syntax error ín URL
     */
    protected String rewriteUrl(Exchange exc, String url, URIFactory uriFactory) throws URISyntaxException {
        return UriUtil.rewrite(uriFactory, url, rewriteProtocol(exc.getInboundProtocol()), rewriteHost(exc.getOriginalHostHeaderHost()), rewritePort(exc.getOriginalHostHeaderPort()));
    }

    private JsonNode rewriteSwagger2(Exchange exc, JsonNode node) {
        JsonNode rewritten = node.deepCopy();
        rewriteHostAndPortSwagger2(exc, rewritten);
        rewriteSchemeSwagger2(exc, rewritten);
        return rewritten;
    }

    private void rewriteSchemeSwagger2(Exchange exc, JsonNode node) {
        // Add protocol http or https
        ArrayNode schemes = ((ObjectNode) node).putArray("schemes");
        schemes.add(exc.getInboundProtocol());
    }

    private void rewriteHostAndPortSwagger2(Exchange exc, JsonNode rewrittenJson) {
        JsonNode host = rewrittenJson.get("host");
        if (host == null)
            return;

        String rewrittenHost = getRewrittenHostAndPortSwagger2(exc);
        ((ObjectNode) rewrittenJson).put("host", rewrittenHost);
        log.debug("Rewriting {} to {}", host, rewrittenHost);
    }

    /**
     * Rewrites Host from <b>Swagger 2.X</b>
     *
     * @param exc Exchange
     * @return Rewritten host with port
     */
    protected String getRewrittenHostAndPortSwagger2(Exchange exc) {
        return rewriteHost(exc.getOriginalHostHeaderHost()) + ":" + rewritePort(exc.getOriginalHostHeaderPort());
    }

    /**
     * Rewrites the protocol if there was a value given.
     *
     * @param protocol from the OpenAPI
     * @return rewritten value
     */
    public String rewriteProtocol(String protocol) {
        return requireNonNullElse(this.protocol, protocol);
    }

    /**
     * Rewrites the host if there was a value given.
     *
     * @param host from the OpenAPI
     * @return rewritten value
     */
    public String rewriteHost(String host) {
        return requireNonNullElse(this.host, host);
    }

    /**
     * Rewrites the port if there was a value given.
     *
     * @param port from the OpenAPI
     * @return rewritten value
     */
    public String rewritePort(String port) {
        if (this.port != null) {
            return this.port.toString();
        }
        return port;
    }


    public int getPort() {
        return port;
    }

    @MCAttribute()
    public void setPort(int port) {
        this.port = port;
    }

    public String getProtocol() {
        return protocol;
    }

    @MCAttribute()
    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getHost() {
        return host;
    }

    @MCAttribute()
    public void setHost(String host) {
        this.host = host;
    }
}
