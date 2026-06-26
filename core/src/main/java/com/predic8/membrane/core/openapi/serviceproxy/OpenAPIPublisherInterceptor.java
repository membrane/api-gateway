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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.util.ConfigurationException;
import com.predic8.membrane.core.util.URIFactory;
import groovy.text.StreamingTemplateEngine;
import groovy.text.Template;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.ObjectMapperFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.predic8.membrane.core.exceptions.ProblemDetails.internal;
import static com.predic8.membrane.core.exceptions.ProblemDetails.user;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.http.MimeType.TEXT_HTML_UTF8;
import static com.predic8.membrane.core.http.Response.ok;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.Set.REQUEST_FLOW;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.openapi.util.OpenAPIUtil.isOpenAPI3;
import static com.predic8.membrane.core.openapi.util.OpenAPIUtil.isSwagger2;
import static com.predic8.membrane.core.openapi.util.UriUtil.getPathFromURL;
import static com.predic8.membrane.core.openapi.util.Utils.getResourceAsStream;
import static java.lang.String.valueOf;

/**
 * @description Serves the OpenAPI documents declared on the enclosing <code>api</code> together with a Swagger UI,
 * under the <code>/api-docs</code> path. <code>/api-docs</code> returns an overview of all documents: an HTML page when
 * the client accepts HTML, otherwise a JSON list with each document's title, version, and links.
 * <code>/api-docs/{id}</code> returns one document as YAML, rewritten so its server URLs point at the gateway, and
 * <code>/api-docs/ui/{id}</code> opens the Swagger UI for that document. An unknown id returns 404; requests to other
 * paths pass through unchanged. Can only be used inside an api. See the examples under examples/openapi and the tutorial
 * tutorials/getting-started/80-OpenAPI.yaml.
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

    private final ObjectMapper om = new ObjectMapper();
    private final ObjectWriter ow = new ObjectMapper().writerWithDefaultPrettyPrinter();
    private final ObjectMapper omYaml = ObjectMapperFactory.createYaml();

    public static final String PATH = "/api-docs";
    public static final String PATH_UI = "/api-docs/ui";

    private static final Pattern PATTERN_META = Pattern.compile(PATH + "?/(.+)");
    private static final Pattern PATTERN_UI = Pattern.compile(PATH + "?/ui/(.+)");

    protected Map<String, OpenAPIRecord> apis;

    private Template swaggerUiHtmlTemplate;
    private Template apiOverviewHtmlTemplate;

    private APIProxy apiProxy;

    /**
     * Needed for instantiation from Spring
     */
    public OpenAPIPublisherInterceptor() {}

    public OpenAPIPublisherInterceptor(Map<String, OpenAPIRecord> apis) {
        this.apis = apis;
    }

    public void init() {
        super.init();
        if (apis == null) {
            if(apiProxy == null) {
                throw new ConfigurationException("<openapiPublisher> can only be used within an <api>");
            }
            apis = apiProxy.apiRecords;
        }

        swaggerUiHtmlTemplate = createHTMLPageTemplate("/openapi/swagger-ui.html");
        checkServerPaths();
        apiOverviewHtmlTemplate = createHTMLPageTemplate("/openapi/overview.html");
    }

    private Template createHTMLPageTemplate(String filePath) {
        try {
            return new StreamingTemplateEngine().createTemplate(new InputStreamReader(Objects.requireNonNull(getResourceAsStream(this, filePath))));
        } catch (Exception e) {
            throw new ConfigurationException("Could not create Swagger UI or overview page template from: %s".formatted(filePath), e);
        }
    }

    @Override
    public Outcome handleRequest(Exchange exc) {

        if (exc.getRequest().getUri().matches(valueOf(PATTERN_UI))) {
            return handleSwaggerUi(exc);
        }

        if (!exc.getRequest().getUri().startsWith("/api-doc"))
            return CONTINUE;

        try {
            return handleOverviewOpenAPIDoc(exc);
        } catch (Exception e) {
            log.error("", e);
            internal(router.getConfiguration().isProduction(), getDisplayName())
                    .detail("Error handling OpenAPI overview!")
                    .exception(e)
                    .buildAndSetResponse(exc);
            return ABORT;
        }
    }

    private Outcome handleOverviewOpenAPIDoc(Exchange exc) throws IOException, URISyntaxException {
        Matcher m = PATTERN_META.matcher(exc.getRequest().getUri());
        if (!m.matches()) { // No id specified
            if (acceptsHtmlExplicit(exc)) {
                return returnHtmlOverview(exc);
            }
            return returnJsonOverview(exc);
        }

        String id = m.group(1);
        OpenAPIRecord rec = apis.get(id);

        if (rec == null) {
            return returnNoFound(exc, id);
        }
        return returnOpenApiAsYaml(exc, rec);
    }

    private Outcome returnJsonOverview(Exchange exc) throws JsonProcessingException {
        exc.setResponse(ok().contentType(APPLICATION_JSON).body(ow.writeValueAsBytes(createDictionaryOfAPIs())).build());
        return RETURN;
    }

    private Outcome returnHtmlOverview(Exchange exc) {
        exc.setResponse(ok().contentType(TEXT_HTML_UTF8).body(renderOverviewTemplate()).build());
        return RETURN;
    }

    private Outcome returnNoFound(Exchange exc, String id) {
        // Do not log. Too common!
        user(false, getDisplayName())
                .title("OpenAPI not found")
                .status(404)
                .addSubType("openapi")
                .addSubSee("wrong-id")
                .detail("OpenAPI document with the id %s not found.".formatted(id))
                .topLevel("id", id)
                .buildAndSetResponse(exc);
        return RETURN;
    }

    private Outcome returnOpenApiAsYaml(Exchange exc, OpenAPIRecord rec) throws IOException, URISyntaxException {
        exc.setResponse(ok().yaml()
                .body(omYaml.writeValueAsBytes(rec.rewriteOpenAPI(exc, getRouter().getConfiguration().getUriFactory())))
                .build());
        return RETURN;
    }

    private Outcome handleSwaggerUi(Exchange exc) {
        Matcher m = PATTERN_UI.matcher(exc.getRequest().getUri());

        // No id specified
        if (!m.matches()) {
            // Do not log! Too common.
            user(false, getDisplayName())
                    .title("No OpenAPI document id")
                    .status(404)
                    .addSubType("openapi")
                    .addSubSee("wrong-id")
                    .detail("Please specify an id of an OpenAPI document. Path should match this pattern: /api-docs/ui/<<id>>")
                    .buildAndSetResponse(exc);
            return RETURN;
        }

        // /api-doc/ui/(.*)
        String id = m.group(1);

        log.debug("OpenAPI with id {} requested", id);

        OpenAPIRecord record = apis.get(id);
        if (record == null) {
            return returnNoFound(exc, id);
        }

        exc.setResponse(ok().contentType(TEXT_HTML_UTF8).body(renderSwaggerUITemplate(id, record.api)).build());

        return RETURN;
    }

    private String renderOverviewTemplate() {
        Map<String, Object> tempCtx = new HashMap<>();
        tempCtx.put("apis", apis);
        tempCtx.put("pathUi", PATH_UI);
        tempCtx.put("path", PATH);
        tempCtx.put("uriFactory", router.getConfiguration().getUriFactory());
        return apiOverviewHtmlTemplate.make(tempCtx).toString();
    }

    private String renderSwaggerUITemplate(String id, OpenAPI api) {
        Map<String, Object> tempCtx = new HashMap<>();
        tempCtx.put("openApiUrl", PATH + "/" + id);
        tempCtx.put("openApiTitle", api.getInfo().getTitle());
        return swaggerUiHtmlTemplate.make(tempCtx).toString();
    }

    private ObjectNode createDictionaryOfAPIs() {
        ObjectNode top = om.createObjectNode();
        for (Map.Entry<String, OpenAPIRecord> api : apis.entrySet()) {
            ObjectNode apiDetails = top.putObject(api.getKey());
            JsonNode node = api.getValue().node;
            apiDetails.put("openapi", getSpecVersion(node));
            apiDetails.put("title", node.get("info").get("title").asText());
            apiDetails.put("version", node.get("info").get("version").asText());
            apiDetails.put("openapi_link", PATH + "/" + api.getKey());
            apiDetails.put("ui_link", PATH + "/ui/" + api.getKey());
        }
        return top;
    }

    private String getSpecVersion(JsonNode node) {
        if (isSwagger2(node)) {
            return node.get("swagger").asText();
        } else if (isOpenAPI3(node)) {
            return node.get("openapi").asText();
        }
        log.info("Unknown OpenAPI version ignoring {}", node);
        return "?";
    }

    /**
     * In the Accept Header is html explicitly mentioned.
     */
    private boolean acceptsHtmlExplicit(Exchange exc) {
        if (exc.getRequest().getHeader().getAccept() == null)
            return false;
        return exc.getRequest().getHeader().getAccept().contains("html");
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