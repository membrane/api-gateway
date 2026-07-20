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

package com.predic8.membrane.core.interceptor.wsdl2openapi;

import com.predic8.membrane.core.util.wsdl.parser.Definitions;
import com.predic8.membrane.core.util.wsdl.parser.Service;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.*;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.parameters.*;
import io.swagger.v3.oas.models.responses.*;
import io.swagger.v3.oas.models.servers.*;
import io.swagger.v3.parser.ObjectMapperFactory;
import org.slf4j.*;

import java.util.*;

/**
 * Generates an OpenAPI 3.0 model from WSDL definitions.
 */
public class OpenApiGenerator {

    private static final Logger log = LoggerFactory.getLogger(OpenApiGenerator.class);

    private final Definitions definitions;
    private final String basePath;
    private final Map<String, OperationConfig> operations;

    public OpenApiGenerator(Definitions definitions, String basePath, Map<String, OperationConfig> operations) {
        this.definitions = definitions;
        this.basePath = basePath.endsWith("/") ? basePath.substring(0, basePath.length() - 1) : basePath;
        this.operations = operations;
    }

    public OpenAPI generate() {
        var openAPI = new OpenAPI();
        openAPI.setOpenapi("3.0.0");
        openAPI.setInfo(buildInfo());
        openAPI.setServers(List.of(new Server().url(basePath)));
        openAPI.setPaths(buildPaths());
        return openAPI;
    }

    public String generateYaml() {
        try {
            return ObjectMapperFactory.createYaml().writeValueAsString(generate());
        } catch (Exception e) {
            throw new RuntimeException("Could not serialize OpenAPI model to YAML", e);
        }
    }

    private Info buildInfo() {
        return new Info()
                .title(getServiceName())
                .description("Auto-generated OpenAPI from WSDL")
                .version("1.0.0");
    }

    private Paths buildPaths() {
        var paths = new Paths();
        for (var entry : operations.entrySet()) {
            String operationName = entry.getKey();
            paths.addPathItem("/" + camelToKebab(operationName), buildPathItem(operationName));
        }
        return paths;
    }

    private PathItem buildPathItem(String operationName) {
        var operation = new Operation()
                .operationId(operationName)
                .summary(operationName)
                .requestBody(buildRequestBody(operationName))
                .responses(buildResponses());
        return new PathItem().post(operation);
    }

    private RequestBody buildRequestBody(String operationName) {
        var schema = new ObjectSchema()
                .description("Request parameters for " + operationName);
        return new RequestBody()
                .required(true)
                .content(new Content().addMediaType("application/json", new MediaType().schema(schema)));
    }

    private ApiResponses buildResponses() {
        return new ApiResponses()
                .addApiResponse("200", new ApiResponse()
                        .description("Successful response")
                        .content(new Content().addMediaType("application/json",
                                new MediaType().schema(new ObjectSchema()))))
                .addApiResponse("500", new ApiResponse().description("Internal server error"));
    }

    private String getServiceName() {
        List<Service> services = definitions.getServices();
        if (!services.isEmpty()) {
            return services.get(0).getName();
        }
        return "SOAP Service";
    }

    private String camelToKebab(String camelCase) {
        var result = new StringBuilder();
        for (int i = 0; i < camelCase.length(); i++) {
            char c = camelCase.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) result.append('-');
                result.append(Character.toLowerCase(c));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
}
