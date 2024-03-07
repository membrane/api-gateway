package com.predic8.membrane.core.openapi.serviceproxy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.predic8.membrane.core.Router;
import groovy.text.StreamingTemplateEngine;
import groovy.text.Template;
import io.swagger.v3.oas.models.OpenAPI;
import org.slf4j.*;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import static com.predic8.membrane.core.openapi.util.OpenAPIUtil.isOpenAPI3;
import static com.predic8.membrane.core.openapi.util.OpenAPIUtil.isSwagger2;
import static com.predic8.membrane.core.openapi.util.Utils.getResourceAsStream;

public class OpenAPIPublisher {

    public static final String PATH = "/api-docs";
    public static final String PATH_UI = "/api-docs/ui";
    private final Template swaggerUiHtmlTemplate;
    private final Template apiOverviewHtmlTemplate;

    private final ObjectMapper om = new ObjectMapper();
    private final ObjectWriter ow = new ObjectMapper().writerWithDefaultPrettyPrinter();

    protected Map<String, OpenAPIRecord> apis;

    public OpenAPIPublisher (Map<String, OpenAPIRecord> apis) throws IOException, ClassNotFoundException {
        this.apis = apis;
        swaggerUiHtmlTemplate = createTemplate("/openapi/swagger-ui.html");
        apiOverviewHtmlTemplate = createTemplate("/openapi/overview.html");
    }

    private Template createTemplate(String filePath) throws ClassNotFoundException, IOException {
        return new StreamingTemplateEngine().createTemplate(new InputStreamReader(getResourceAsStream(this, filePath)));
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
