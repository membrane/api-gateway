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

import com.predic8.membrane.core.util.wsdl.parser.*;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.servers.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.predic8.membrane.core.interceptor.wsdl2openapi.XsdDomUtil.camelToKebab;
import static com.predic8.membrane.core.util.wsdl.parser.Operation.Direction.INPUT;
import static com.predic8.membrane.core.util.wsdl.parser.Operation.Direction.OUTPUT;
import static io.swagger.v3.parser.ObjectMapperFactory.createYaml;

/**
 * Generates an OpenAPI 3.0 model from WSDL definitions.
 */
public class Wsdl2OpenApiConverter {

    private static final Logger log = LoggerFactory.getLogger(Wsdl2OpenApiConverter.class);

    private final Definitions definitions;
    private final String basePath;
    private final XsdToSchema converter;
    private final Map<String, OperationConfig> operations;

    public Wsdl2OpenApiConverter(Definitions definitions, String basePath) {
        this(definitions, basePath, Map.of());
    }

    public Wsdl2OpenApiConverter(Definitions definitions, String basePath, Map<String, OperationConfig> operations) {
        this.definitions = definitions;
        this.basePath = basePath.endsWith("/") ? basePath.substring(0, basePath.length() - 1) : basePath;
        this.converter = new XsdToSchema(definitions);
        this.operations = operations;
    }

    public OpenAPI generate() {
        var openAPI = new OpenAPI();
        openAPI.setOpenapi("3.1.0"); // TODO Newest 3.1
        openAPI.setInfo(buildInfo());
        openAPI.setServers(List.of(new Server().url(basePath)));
        openAPI.setPaths(buildPaths());
        return openAPI;
    }

    public String generateYaml() {
        try {
            return createYaml().writeValueAsString(generate());
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
        if (operations.isEmpty()) {
            definitions.getPortTypes().stream()
                    .flatMap(pt -> pt.getOperations().stream())
                    .filter(wsdlOp -> {
                        if (wsdlOp.getName() == null) {
                            log.debug("Skipping WSDL operation with null name");
                            return false;
                        }
                        return true;
                    })
                    .forEach(wsdlOp -> paths.addPathItem("/" + camelToKebab(wsdlOp.getName()), buildPathItem(wsdlOp.getName(), wsdlOp, "POST")));
        } else {
            var wsdlOps = definitions.getPortTypes().stream()
                    .flatMap(pt -> pt.getOperations().stream())
                    .filter(op -> op.getName() != null)
                    .toList();
            for (var entry : operations.entrySet()) {
                var name = entry.getKey();
                var opConfig = entry.getValue();
                var wsdlOp = wsdlOps.stream().filter(op -> name.equals(op.getName())).findFirst().orElse(null);
                if (wsdlOp == null) log.debug("Configured operation '{}' not found in WSDL definitions", name);
                var pathSegment = opConfig.getPath() != null ? opConfig.getPath() : camelToKebab(name);
                paths.addPathItem("/" + pathSegment, buildPathItem(name, wsdlOp, opConfig.getMethod()));
            }
        }
        return paths;
    }

    private PathItem buildPathItem(String name, Operation wsdlOp, String method) {
        var inputParts = getInputParts(wsdlOp);
        var headerParts = findBindingOperation(name).map(this::getHeaderParts).orElse(List.of());

        var apiOp = new io.swagger.v3.oas.models.Operation()
                .operationId(name)
                .summary(name)
                .responses(buildResponses(wsdlOp));
        if (hasRequestBody(method)) {
            apiOp.requestBody(buildRequestBody(getBodyParts(inputParts, headerParts)));
        }
        var headerParameters = buildHeaderParameters(headerParts);
        if (!headerParameters.isEmpty()) {
            apiOp.setParameters(headerParameters);
        }
        return applyMethod(new PathItem(), apiOp, method);
    }

    private static boolean hasRequestBody(String method) {
        return !method.equalsIgnoreCase("GET") && !method.equalsIgnoreCase("DELETE");
    }

    private static PathItem applyMethod(PathItem item, io.swagger.v3.oas.models.Operation op, String method) {
        return switch (method.toUpperCase()) {
            case "GET"    -> item.get(op);
            case "PUT"    -> item.put(op);
            case "DELETE" -> item.delete(op);
            case "PATCH"  -> item.patch(op);
            default       -> item.post(op);
        };
    }

    private List<Part> getInputParts(Operation wsdlOp) {
        var inputs = wsdlOp.getMessagesByDirection(INPUT);
        return inputs.isEmpty() ? List.of() : inputs.getFirst().getParts();
    }

    private List<Part> getBodyParts(List<Part> inputParts, List<Part> headerParts) {
        var headerPartNames = headerParts.stream().map(Part::getName).collect(Collectors.toSet());
        return inputParts.stream().filter(p -> !headerPartNames.contains(p.getName())).toList();
    }

    private List<Parameter> buildHeaderParameters(List<Part> headerParts) {
        return headerParts.stream().map(this::buildHeaderParameter).toList();
    }

    private Optional<BindingOperation> findBindingOperation(String name) {
        return definitions.getBindings().stream()
                .flatMap(b -> b.getBindingOperations().stream())
                .filter(bo -> name.equals(bo.getName()))
                .findFirst();
    }

    private List<Part> getHeaderParts(BindingOperation bindingOperation) {
        return bindingOperation.getInputs().stream()
                .flatMap(input -> input.getHeaders().stream())
                .map(this::resolveHeaderPart)
                .filter(Objects::nonNull)
                .toList();
    }

    private Part resolveHeaderPart(SoapHeader header) {
        return definitions.findMessage(WSDLParserUtil.getLocalName(header.getMessage()))
                .flatMap(message -> message.getParts().stream()
                        .filter(p -> header.getPart().equals(p.getName()))
                        .findFirst())
                .orElse(null);
    }

    private Parameter buildHeaderParameter(Part part) {
        var schema = part.getElementQName() != null
                ? converter.convert(part.getElementQName())
                : converter.convertType(part.getTypeQName());
        return new Parameter().in("header").name(part.getName()).schema(schema);
    }

    private RequestBody buildRequestBody(List<Part> parts) {
        var schema = converter.convertParts(parts);
        return new RequestBody()
                .required(true)
                .content(new Content().addMediaType("application/json", new MediaType().schema(schema)));
    }

    private ApiResponses buildResponses(Operation wsdlOp) {
        var schema = converter.convertMessageParts(wsdlOp.getMessagesByDirection(OUTPUT));
        var response200 = new ApiResponse()
                .description("Successful response")
                .content(new Content().addMediaType("application/json", new MediaType().schema(schema)));

        List<Schema> faultSchemas = wsdlOp.getFaults().stream()
                .map(fault -> fault.getMessage().getParts())
                .filter(parts -> !parts.isEmpty())
                .map(parts -> parts.getFirst().getElementQName())
                .filter(Objects::nonNull)
                .map(qname -> (Schema) converter.convert(qname))
                .toList();

        ApiResponse response500;
        if (faultSchemas.isEmpty()) {
            response500 = new ApiResponse().description("Internal server error");
        } else {
            Schema faultSchema = faultSchemas.size() == 1
                    ? faultSchemas.getFirst()
                    : new ComposedSchema().oneOf(faultSchemas);
            response500 = new ApiResponse()
                    .description("Internal server error")
                    .content(new Content().addMediaType("application/json", new MediaType().schema(faultSchema)));
        }

        return new ApiResponses()
                .addApiResponse("200", response200)
                .addApiResponse("500", response500);
    }

    private String getServiceName() {
        List<Service> services = definitions.getServices();
        if (!services.isEmpty()) {
            return services.getFirst().getName();
        }
        return "SOAP Service";
    }

}
