/*
 *  Copyright 2022 predic8 GmbH, www.predic8.com
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

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.util.ConfigurationException;
import com.predic8.membrane.core.util.URIFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;
import java.util.EnumSet;
import java.util.Map;

import static com.predic8.membrane.core.exceptions.ProblemDetails.internal;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.Set.REQUEST_FLOW;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.openapi.util.UriUtil.getPathFromURL;

/**
 * @description Serves the OpenAPI documents declared on the enclosing <code>api</code> together with a Swagger UI,
 * under the <code>/api-docs</code> path. <code>/api-docs</code> returns an overview of all documents: an HTML page when
 * the client accepts HTML, otherwise a JSON list with each document's title, version, and links.
 * <code>/api-docs/{id}</code> returns one document as YAML, rewritten so its server URLs point at the gateway, and
 * <code>/api-docs/ui/{id}</code> opens the Swagger UI for that document. An unknown id returns 404; requests to other
 * paths pass through unchanged. Can only be used inside an api. See the tutorial
 * tutorials/getting-started/120-OpenAPI.yaml.
 * @topic 5. OpenAPI
 * @yaml
 * <pre><code>
 * api:
 *   port: 2000
 *   openapi:
 *     - location: fruitshop-api.yml
 *   flow:
 *     - openapiPublisher: {}
 * </code></pre>
 */
@MCElement(name = "openapiPublisher")
public class OpenAPIPublisherInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(OpenAPIPublisherInterceptor.class.getName());

    protected Map<String, OpenAPIRecord> apis;

    private OpenAPIPublisher publisher;

    private APIProxy apiProxy;

    /**
     * Needed for instantiation from Spring
     */
    public OpenAPIPublisherInterceptor() {}

    public OpenAPIPublisherInterceptor(Map<String, OpenAPIRecord> apis) {
        this.apis = apis;
    }

    /**
     * Adds a record under an id derived from the OpenAPI document. The id is computed here rather
     * than passed in, so that every insertion into {@code apis} goes through the same collision
     * policy as {@link OpenAPIRecordFactory}: a colliding document is kept under a suffixed id
     * instead of silently replacing the record already registered.
     */
    public void addRecord(OpenAPIRecord record) {
        apis.put(OpenAPIRecordFactory.getUniqueId(apis, record), record);
    }

    public void init() {
        super.init();
        if (apis == null) {
            if(apiProxy == null) {
                throw new ConfigurationException("<openapiPublisher> can only be used within an <api>");
            }
            apis = apiProxy.apiRecords;
        }

        publisher = new OpenAPIPublisher(apis, getDisplayName());
        checkServerPaths();
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        if (!exc.getRequest().getRoutingPath().startsWith("/api-doc"))
            return CONTINUE;

        try {
            return publisher.handle(exc, router);
        } catch (Exception e) {
            log.error("", e);
            internal(router.getConfiguration().isProduction(), getDisplayName())
                    .detail("Error handling OpenAPI overview!")
                    .exception(e)
                    .buildAndSetResponse(exc);
            return ABORT;
        }
    }

    @Override
    public String getShortDescription() {
        return "Publishes the OpenAPI description and Swagger UI.";
    }

    private void checkServerPaths() {
        if (apis.size() <= 1)
            return;

        apis.values().stream()
                .filter(this::hasPathMatchingAllRequests)
                .forEach(apiRecord -> log.warn("API {} contains URLs with '/' matching all requests. This might cause routing to the wrong API!", apiRecord.api.getInfo().getTitle()));

    }

    private boolean hasPathMatchingAllRequests(OpenAPIRecord apiRecord) {
        return apiRecord.api.getServers().stream()
                .map(server -> {
                    try {
                        return getPathFromURL(new URIFactory(), server.getUrl());
                    } catch (URISyntaxException e) {
                        throw new RuntimeException(e);
                    }
                })
                .anyMatch(serverUrl -> serverUrl == null || serverUrl.isEmpty() || serverUrl.equals("/"));
    }

    @Override
    public String getDisplayName() {
        return "openapi publisher";
    }

    @Override
    public EnumSet<Flow> getAppliedFlow() {
        return REQUEST_FLOW;
    }

    public void setApiProxy(APIProxy apiProxy) {
        this.apiProxy = apiProxy;
    }
}