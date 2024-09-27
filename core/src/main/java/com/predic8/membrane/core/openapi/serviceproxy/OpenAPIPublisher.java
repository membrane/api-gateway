/* Copyright 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.openapi.serviceproxy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exceptions.*;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.Outcome;
import groovy.text.StreamingTemplateEngine;
import groovy.text.Template;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.ObjectMapperFactory;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.http.MimeType.TEXT_HTML_UTF8;
import static com.predic8.membrane.core.http.Response.ok;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static com.predic8.membrane.core.openapi.util.OpenAPIUtil.isOpenAPI3;
import static com.predic8.membrane.core.openapi.util.OpenAPIUtil.isSwagger2;
import static com.predic8.membrane.core.openapi.util.Utils.getFileResourceAsStream;

public class OpenAPIPublisher {

    public static final String PATH = "/api-docs";
    public static final String PATH_UI = "/api-docs/ui";
    private static final Pattern PATTERN_META = Pattern.compile(PATH + "?/(.*)");
    private static final Pattern PATTERN_UI = Pattern.compile(PATH + "?/ui/(.*)");

    private final Template swaggerUiHtmlTemplate;
    private final Template apiOverviewHtmlTemplate;

    private final ObjectMapper om = new ObjectMapper();
    private final ObjectWriter ow = new ObjectMapper().writerWithDefaultPrettyPrinter();
    private final ObjectMapper omYaml = ObjectMapperFactory.createYaml();

    protected Map<String, OpenAPIRecord> apis;

    public OpenAPIPublisher(Map<String, OpenAPIRecord> apis) throws IOException, ClassNotFoundException, URISyntaxException {
        this.apis = apis;
        swaggerUiHtmlTemplate = createTemplate("/openapi/swagger-ui.html");
        apiOverviewHtmlTemplate = createTemplate("/openapi/overview.html");
    }

    Outcome handleSwaggerUi(Exchange exc) {
        Matcher m = PATTERN_UI.matcher(exc.getRequest().getUri());

        // No id specified
        if (!m.matches()) {
            exc.setResponse(ProblemDetails.openapi(false)
                    .statusCode(404)
                    .addSubType("wrong-id")
                    .title("No OpenAPI document id")
                    .detail("Please specify an id of an OpenAPI document. Path should match this pattern: /api-docs/ui/<<id>>").build());
            return RETURN;
        }

        // /api-doc/ui/(.*)
        String id = m.group(1);

        OpenAPIRecord record = apis.get(id);
        if (record == null) {
            return returnNoFound(exc, id);
        }

        exc.setResponse(ok().contentType(TEXT_HTML_UTF8).body(renderSwaggerUITemplate(id, record.api)).build());

        return RETURN;
    }

    Outcome handleOverviewOpenAPIDoc(Exchange exc, Router router, Logger log) throws IOException, URISyntaxException {
        Matcher m = PATTERN_META.matcher(exc.getRequest().getUri());
        if (!m.matches()) { // No id specified
            if (acceptsHtmlExplicit(exc)) {
                return returnHtmlOverview(exc, router);
            }
            return returnJsonOverview(exc, log);
        }

        String id = m.group(1);
        OpenAPIRecord rec = apis.get(id);

        if (rec == null) {
            return returnNoFound(exc, id);
        }
        return returnOpenApiAsYaml(exc, rec, router);
    }

    private boolean acceptsHtmlExplicit(Exchange exc) {
        if (exc.getRequest().getHeader().getAccept() == null)
            return false;
        return exc.getRequest().getHeader().getAccept().contains("html");
    }

    private Outcome returnJsonOverview(Exchange exc, Logger log) throws JsonProcessingException {
        exc.setResponse(ok().contentType(APPLICATION_JSON).body(ow.writeValueAsBytes(createDictionaryOfAPIs(log))).build());
        return RETURN;
    }

    private Outcome returnHtmlOverview(Exchange exc, Router router) {
        exc.setResponse(ok().contentType(TEXT_HTML_UTF8).body(renderOverviewTemplate(router)).build());
        return RETURN;
    }

    private Outcome returnNoFound(Exchange exc, String id) {
        exc.setResponse(ProblemDetails.openapi(false)
                .statusCode(404)
                .addSubType("wrong-id")
                .title("OpenAPI not found.")
                .detail("OpenAPI document with the id %s not found.".formatted(id)).build());
        return RETURN;
    }

    private Outcome returnOpenApiAsYaml(Exchange exc, OpenAPIRecord rec, Router router) throws IOException, URISyntaxException {
        exc.setResponse(ok().yaml()
                .body(omYaml.writeValueAsBytes(rec.rewriteOpenAPI(exc, router.getUriFactory())))
                .build());
        return RETURN;
    }

    private Template createTemplate(String filePath) throws ClassNotFoundException, IOException, URISyntaxException {
        return new StreamingTemplateEngine().createTemplate(new InputStreamReader(Objects.requireNonNull(getFileResourceAsStream(this, filePath))));
    }

    private String renderOverviewTemplate(Router router) {
        Map<String, Object> tempCtx = new HashMap<>();
        tempCtx.put("apis", apis);
        tempCtx.put("pathUi", PATH_UI);
        tempCtx.put("path", PATH);
        tempCtx.put("uriFactory", router.getUriFactory());
        return apiOverviewHtmlTemplate.make(tempCtx).toString();
    }

    private String renderSwaggerUITemplate(String id, OpenAPI api) {
        Map<String, Object> tempCtx = new HashMap<>();
        tempCtx.put("openApiUrl", PATH + "/" + id);
        tempCtx.put("openApiTitle", api.getInfo().getTitle());
        return swaggerUiHtmlTemplate.make(tempCtx).toString();
    }

    private ObjectNode createDictionaryOfAPIs(Logger log) {
        ObjectNode top = om.createObjectNode();
        for (Map.Entry<String, OpenAPIRecord> api : apis.entrySet()) {
            ObjectNode apiDetails = top.putObject(api.getKey());
            JsonNode node = api.getValue().node;
            apiDetails.put("openapi", getSpecVersion(node, log));
            apiDetails.put("title", node.get("info").get("title").asText());
            apiDetails.put("version", node.get("info").get("version").asText());
            apiDetails.put("openapi_link", PATH + "/" + api.getKey());
            apiDetails.put("ui_link", PATH + "/ui/" + api.getKey());
        }
        return top;
    }

    private String getSpecVersion(JsonNode node, Logger log) {
        if (isSwagger2(node)) {
            return node.get("swagger").asText();
        } else if (isOpenAPI3(node)) {
            return node.get("openapi").asText();
        }
        log.info("Unknown OpenAPI version ignoring {}", node);
        return "?";
    }
}
