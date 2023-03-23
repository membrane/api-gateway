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

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import groovy.text.*;
import io.swagger.v3.parser.*;
import org.slf4j.*;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.openapi.util.OpenAPIUtil.*;
import static com.predic8.membrane.core.openapi.util.UriUtil.*;
import static com.predic8.membrane.core.openapi.util.Utils.*;
import static java.lang.Integer.*;
import static java.lang.String.*;

public class OpenAPIPublisherInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(OpenAPIPublisherInterceptor.class.getName());

    public static final String HTML_UTF_8 = "text/html; charset=utf-8";
    private final ObjectMapper om = new ObjectMapper();
    private final ObjectWriter ow = new ObjectMapper().writerWithDefaultPrettyPrinter();
    private final ObjectMapper omYaml = ObjectMapperFactory.createYaml();

    public static final String PATH = "/api-doc";
    public static final String PATH_UI = "/api-doc/ui";

    private static final Pattern patternMeta = Pattern.compile(PATH + "/(.*)");
    private static final Pattern patternUI = Pattern.compile(PATH_UI + "/(.*)");

    protected Map<String, OpenAPIRecord> apis;

    private final Template swaggerUiHtmlTemplate;
    private final Template apiOverviewHtmlTemplate;

    public OpenAPIPublisherInterceptor(Map<String, OpenAPIRecord> apis) throws IOException, ClassNotFoundException {
        name = "OpenAPI Publisher";
        this.apis = apis;
        swaggerUiHtmlTemplate = createTemplate("/openapi/swagger-ui.html");
        apiOverviewHtmlTemplate = createTemplate("/openapi/overview.html");
    }

    private Template createTemplate(String filePath) throws ClassNotFoundException, IOException {
        return new StreamingTemplateEngine().createTemplate(new InputStreamReader(getResourceAsStream(this, filePath)));
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {

        if (!exc.getRequest().getUri().startsWith(PATH))
            return CONTINUE;

        if (exc.getRequest().getUri().startsWith(PATH_UI)) {
            return handleSwaggerUi(exc);
        }

        return handleOverviewOpenAPIDoc(exc);
    }

    private Outcome handleOverviewOpenAPIDoc(Exchange exc) throws JsonProcessingException, URISyntaxException {
        Matcher m = patternMeta.matcher(exc.getRequest().getUri());
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
        exc.setResponse(Response.ok().contentType(APPLICATION_JSON).body(ow.writeValueAsBytes(createDictionaryOfAPIs())).build());
        return RETURN;
    }

    private Outcome returnHtmlOverview(Exchange exc) {
        exc.setResponse(Response.ok().contentType(HTML_UTF_8).body(renderOverviewTemplate()).build());
        return RETURN;
    }

    private Outcome returnNoFound(Exchange exc,String id) {
        exc.setResponse(Response.notFound().contentType(APPLICATION_JSON).body(createErrorMessage(format("OpenAPI document with the id '%s' not found.",id))).build());
        return RETURN;
    }

    private Outcome returnOpenApiAsYaml(Exchange exc, OpenAPIRecord rec) throws JsonProcessingException, URISyntaxException {
        rewriteOpenAPIaccordingToRequest(exc, rec);
        exc.setResponse(Response.ok().contentType(APPLICATION_X_YAML).body(omYaml.writeValueAsBytes(rec.node)).build());
        return RETURN;
    }

    protected void rewriteOpenAPIaccordingToRequest(Exchange exc, OpenAPIRecord rec) throws URISyntaxException {
        rewriteOpenAPIVersion3(exc, rec);
        rewriteSwaggerVersion2(exc, rec);
    }

    private void rewriteSwaggerVersion2(Exchange exc, OpenAPIRecord rec) {
        // Rewrite OpenAPI 2.X
        JsonNode host = rec.node.get("host");
        if (host == null)
            return;

        String rewrittenHost = rewriteHost(exc);
        ((ObjectNode) rec.node).put("host", rewrittenHost);

        // Add protocol http or https
        ArrayNode schemes =  ((ObjectNode) rec.node).putArray("schemes");
        schemes.add(getProtocol(exc));

        log.debug("Rewriting {} to {}",host,rewrittenHost);
    }

    private void rewriteOpenAPIVersion3(Exchange exc, OpenAPIRecord rec) throws URISyntaxException {
        JsonNode servers = rec.node.get("servers");
        if (servers == null)
            return;

        for (JsonNode server : servers) {
            String serverUrl = server.get("url").asText();
            String rewrittenUrl = rewriteUrl(exc, serverUrl);
            ((ObjectNode) server).put("url", rewrittenUrl);
            log.debug("Rewriting {} to {}",serverUrl,rewrittenUrl);
        }
    }

    /**
     * Rewrites URL from <b>OpenAPI 3.X</b>
     * @param exc Exchange
     * @param url URL to rewrite
     * @return Rewritten URL
     * @throws URISyntaxException syntax error ín URL
     */
    protected String rewriteUrl(Exchange exc, String url) throws URISyntaxException {
        return rewriteURL(router.getUriFactory(), url,
                getProtocol(exc),
                exc.getOriginalHostHeaderHost(),
                exc.getOriginalHostHeaderPort());
    }

    /**
     * Rewrites Host from <b>Swagger 2.X</b>
     * @param exc Exchange
     * @return Rewritten host with port
     */
    protected String rewriteHost(Exchange exc) {
        return exc.getOriginalHostHeaderHost() + ":" + exc.getOriginalHostHeaderPort();
    }

    private String getProtocol(Exchange exc) {
        if (exc.getRule().getSslInboundContext() == null)
            return  "http";
        else
            return  "https";
    }

    private Outcome handleSwaggerUi(Exchange exc) {
        Matcher m = patternUI.matcher(exc.getRequest().getUri());
        if (!m.matches()) { // No id specified
            exc.setResponse(Response.ok().contentType(APPLICATION_JSON).body("Please specify an Id").build());
            return RETURN;
        }
        exc.setResponse(Response.ok().contentType(HTML_UTF_8).body(renderSwaggerUITemplate(m.group(1))).build());
        return RETURN;
    }

    private String renderOverviewTemplate() {
        Map<String, Object> tempCtx = new HashMap<>();
        tempCtx.put("apis", apis);
        tempCtx.put("pathUi", PATH_UI);
        tempCtx.put("path", PATH);
        tempCtx.put("uriFactory", router.getUriFactory());
        return apiOverviewHtmlTemplate.make(tempCtx).toString();
    }

    private String renderSwaggerUITemplate(String id) {
        Map<String, Object> tempCtx = new HashMap<>();
        tempCtx.put("openApiUrl", PATH + "/" + id);
        tempCtx.put("openApiTitle", apis.get(id).api.getInfo().getTitle());
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
        log.info("Unknown OpenAPI version ignoring {}",node);
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
}