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

import com.predic8.membrane.core.util.ConfigurationException;
import com.predic8.membrane.core.util.wsdl.parser.*;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.interceptor.wsdl2openapi.Wsdl2OpenapiInterceptor.extractParamNames;
import static com.predic8.membrane.core.interceptor.wsdl2openapi.XsdDomUtil.camelToKebab;
import static com.predic8.membrane.core.util.wsdl.parser.Operation.Direction.INPUT;
import static com.predic8.membrane.core.util.wsdl.parser.Operation.Direction.OUTPUT;
import static io.swagger.v3.parser.ObjectMapperFactory.createYaml;

/**
 * Generates an OpenAPI 3.1 model from WSDL definitions.
 */
public class Wsdl2OpenApiConverter {

    private static final Logger log = LoggerFactory.getLogger(Wsdl2OpenApiConverter.class);

    /** Mapping of an operation the configuration says nothing about: POST on its kebab-case name. */
    private static final OperationSettings DEFAULT_SETTINGS = new OperationSettings();

    private final Definitions definitions;
    private final String basePath;
    private final XsdToSchema converter;
    private final Map<String, OperationSettings> operations;

    public Wsdl2OpenApiConverter(Definitions definitions, String basePath) {
        this(definitions, basePath, Map.of());
    }

    public Wsdl2OpenApiConverter(Definitions definitions, String basePath, Map<String, OperationSettings> operations) {
        this.definitions = definitions;
        this.basePath = stripTrailingSlash(basePath);
        this.converter = new XsdToSchema(definitions);
        this.operations = operations;
    }

    /**
     * Strips one trailing slash from a base path, except from the root path {@code "/"}: an empty
     * server url would make Swagger UI resolve requests relative to the /api-docs page instead of
     * the API root.
     */
    static String stripTrailingSlash(String basePath) {
        return basePath.length() > 1 && basePath.endsWith("/")
                ? basePath.substring(0, basePath.length() - 1)
                : basePath;
    }

    public OpenAPI generate() {
        var openAPI = new OpenAPI();
        openAPI.setOpenapi("3.1.2");
        openAPI.setInfo(buildInfo());
        openAPI.setServers(List.of(new Server().url(basePath)));
        openAPI.setPaths(buildPaths());
        var topLevelTags = buildTopLevelTags();
        if (!topLevelTags.isEmpty()) {
            openAPI.setTags(topLevelTags);
        }
        return openAPI;
    }

    private List<Tag> buildTopLevelTags() {
        return operations.values().stream()
                .map(OperationSettings::getTag)
                .filter(Objects::nonNull)
                .distinct()
                .map(name -> new Tag().name(name))
                .toList();
    }

    public String generateYaml() {
        var openAPI = generate();
        try {
            return createYaml().writeValueAsString(openAPI);
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
        var wsdlOps = namedWsdlOperations();
        if (operations.isEmpty()) {
            wsdlOps.forEach(wsdlOp -> paths.addPathItem(
                    "/" + camelToKebab(wsdlOp.getName()), buildPathItem(wsdlOp.getName(), wsdlOp, DEFAULT_SETTINGS)));
            return paths;
        }
        operations.forEach((name, opSettings) -> addConfiguredPath(paths, wsdlOps, name, opSettings));
        return paths;
    }

    /** The WSDL's operations across all port types; unnamed ones cannot be mapped to a path. */
    private List<Operation> namedWsdlOperations() {
        var named = new ArrayList<Operation>();
        for (var portType : definitions.getPortTypes()) {
            for (var wsdlOp : portType.getOperations()) {
                if (wsdlOp.getName() == null) {
                    log.debug("Skipping WSDL operation with null name");
                    continue;
                }
                named.add(wsdlOp);
            }
        }
        return named;
    }

    private void addConfiguredPath(Paths paths, List<Operation> wsdlOps, String name, OperationSettings opSettings) {
        var wsdlOp = wsdlOps.stream().filter(op -> name.equals(op.getName())).findFirst()
                .orElseThrow(() -> new ConfigurationException(
                        "Operation '%s' is not defined in the WSDL. Available operations: %s".formatted(
                                name, wsdlOps.stream().map(Operation::getName).toList())));
        var pathKey = "/" + (opSettings.getPath() != null ? opSettings.getPath() : camelToKebab(name));
        var existing = paths.get(pathKey);
        if (existing != null) {
            applyMethod(existing, buildApiOperation(name, wsdlOp, opSettings), opSettings.getMethod());
        } else {
            paths.addPathItem(pathKey, buildPathItem(name, wsdlOp, opSettings));
        }
    }

    private PathItem buildPathItem(String name, Operation wsdlOp, OperationSettings settings) {
        return applyMethod(new PathItem(), buildApiOperation(name, wsdlOp, settings), settings.getMethod());
    }

    private io.swagger.v3.oas.models.Operation buildApiOperation(String name, Operation wsdlOp, OperationSettings settings) {
        var inputParts = getInputParts(wsdlOp);
        var headerParts = findBindingOperation(name).map(this::getHeaderParts).orElse(List.of());

        var apiOp = new io.swagger.v3.oas.models.Operation()
                .operationId(name)
                .summary(name)
                .responses(buildResponses(wsdlOp));

        if (settings.getTag() != null) {
            apiOp.addTagsItem(settings.getTag());
        }

        if (settings.getPath() != null) {
            extractParamNames(settings.getPath()).forEach(p ->
                apiOp.addParametersItem(new Parameter().name(p).in("path").required(true).schema(new StringSchema()))
            );
        }
        if (hasRequestBody(settings.getMethod())) {
            apiOp.requestBody(buildRequestBody(getBodyParts(inputParts, headerParts)));
        }
        headerParts.stream().map(this::buildHeaderParameter).forEach(apiOp::addParametersItem);
        return apiOp;
    }

    private static boolean hasRequestBody(String method) {
        return !method.equalsIgnoreCase("GET") && !method.equalsIgnoreCase("DELETE");
    }

    private static PathItem applyMethod(PathItem item, io.swagger.v3.oas.models.Operation op, String method) {
        return switch (method.toUpperCase()) {
            case "GET"    -> item.get(op);
            case "POST"   -> item.post(op);
            case "PUT"    -> item.put(op);
            case "DELETE" -> item.delete(op);
            case "PATCH"  -> item.patch(op);
            // OperationSettings.setMethod already rejects anything else at config time, so this
            // is a bug rather than bad configuration.
            default       -> throw new IllegalStateException("Unvalidated HTTP method reached the converter: " + method);
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
        return new RequestBody()
                .required(true)
                .content(jsonContent(converter.convertParts(parts)));
    }

    private static Content jsonContent(Schema<?> schema) {
        return new Content().addMediaType(APPLICATION_JSON, new MediaType().schema(schema));
    }

    private ApiResponses buildResponses(Operation wsdlOp) {
        var response200 = new ApiResponse()
                .description("Successful response")
                .content(jsonContent(converter.convertMessageParts(wsdlOp.getMessagesByDirection(OUTPUT))));

        return new ApiResponses()
                .addApiResponse("200", response200)
                .addApiResponse("500", buildFaultResponse(wsdlOp));
    }

    /** The 500 response: the operation's declared faults, combined with oneOf if there is more than one. */
    private ApiResponse buildFaultResponse(Operation wsdlOp) {
        List<Schema> faultSchemas = wsdlOp.getFaults().stream()
                .map(fault -> fault.getMessage().getParts())
                .filter(parts -> !parts.isEmpty())
                .map(parts -> (Schema) converter.convertParts(parts))
                .toList();

        var response = new ApiResponse().description("Internal server error");
        if (faultSchemas.isEmpty()) return response;

        Schema faultSchema = faultSchemas.size() == 1
                ? faultSchemas.getFirst()
                : new ComposedSchema().oneOf(faultSchemas);
        return response.content(jsonContent(faultSchema));
    }

    private String getServiceName() {
        List<Service> services = definitions.getServices();
        if (!services.isEmpty()) {
            return services.getFirst().getName();
        }
        return "SOAP Service";
    }

}
