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

package com.predic8.membrane.core.openapi.util;

import com.fasterxml.jackson.databind.*;
import io.swagger.v3.core.util.*;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.parameters.*;
import io.swagger.v3.parser.ObjectMapperFactory;
import org.slf4j.*;

import java.io.*;
import java.util.*;

import static com.predic8.membrane.core.openapi.serviceproxy.APIProxy.*;
import static com.predic8.membrane.core.openapi.util.Utils.*;
import static com.predic8.membrane.core.openapi.validators.JsonSchemaValidator.OBJECT;
import static io.swagger.v3.oas.models.parameters.Parameter.StyleEnum.FORM;
import static java.lang.Boolean.TRUE;

public class OpenAPIUtil {

    private static final Logger log = LoggerFactory.getLogger(OpenAPIUtil.class.getName());

    private static final ObjectMapper omYaml = ObjectMapperFactory.createYaml();

    public static String getIdFromAPI(OpenAPI api) {
        if (api.getInfo().getExtensions() != null) {
            String id = (String) api.getInfo().getExtensions().get(X_MEMBRANE_ID);
            if (id != null)
                return normalizeForId(id + getVersionSuffix(api));
        }
        return normalizeForId(api.getInfo().getTitle() + "-v" + api.getInfo().getVersion());
    }

    private static String getVersionSuffix(OpenAPI api) {
        return "-v" + api.getInfo().getVersion();
    }

    public static String getOpenAPIVersion(JsonNode node) {
        if (isSwagger2(node)) {
            return node.get("swagger").asText();
        } else if (isOpenAPI3(node)) {
            return node.get("openapi").asText();
        }
        log.info("Cannot detect OpenAPI version.");
        return "?";
    }

    public static boolean isOpenAPI3(JsonNode node) {
        return node.get("openapi") != null && node.get("openapi").asText().startsWith("3");
    }

    public static boolean isSwagger2(JsonNode node) {
        return node.get("swagger") != null && node.get("swagger").asText().startsWith("2");
    }

    public static JsonNode convert2Json(OpenAPI api) throws IOException {
        return omYaml.readTree(Json31.mapper().writeValueAsBytes(api));
    }

    public static boolean isOpenAPIMisplacedError(String errorMsg) {
        return errorMsg.matches("(?i).*invalid.+element.+http://membrane-soa.org/proxies/1/\":openapi.*'\\..*");
    }

    public static PathItem getPath(OpenAPI api, String path) {
        return api.getPaths().get(path);
    }

    public static Parameter getParameter(Operation operation, String parameterName) {
        if (operation == null || operation.getParameters() == null) return null;
        return operation.getParameters().stream()
                .filter(Objects::nonNull)
                .filter(p -> parameterName.equals(p.getName()))
                .findFirst()
                .orElse(null);
    }

    public static Schema<?> getProperty(Schema<?> schema, String propertyName) {
        if (schema == null || schema.getProperties() == null) return null;
        return (Schema<?>) schema.getProperties().get(propertyName);
    }

    public static Schema<?> resolveSchema(OpenAPI api, Parameter p) {
        Schema<?> schema = p.getSchema();
        if (schema == null) {
            return null;
        }
        if (schema.get$ref() != null) {
            if (api.getComponents() == null || api.getComponents().getSchemas() == null) return null;
            return api.getComponents().getSchemas().get(getComponentLocalNameFromRef(schema.get$ref()));
        }
        return schema;
    }

    /**
     * If schema has type: object or type: [..., object]
     * @param schema
     * @return
     */
    public static boolean hasObjectType(Schema schema) {
        return (schema.getTypes() != null && (schema.getTypes().contains(OBJECT)) || OBJECT.equals(schema.getType()));
    }

    public static boolean isExplode(Parameter parameter) {
        if (parameter.getExplode() == null) {
            if (parameter.getStyle() == FORM) {
                return true;
            }
        }
        return TRUE.equals(parameter.getExplode());
    }
}
