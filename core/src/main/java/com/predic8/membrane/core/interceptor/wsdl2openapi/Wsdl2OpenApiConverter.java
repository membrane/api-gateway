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
import io.swagger.v3.core.util.Yaml31;
import io.swagger.v3.oas.models.Components;
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
import static com.predic8.membrane.core.http.MimeType.APPLICATION_PROBLEM_JSON;
import static com.predic8.membrane.core.interceptor.wsdl2openapi.OperationRouter.extractParamNames;
import static com.predic8.membrane.core.interceptor.wsdl2openapi.XsdDomUtil.*;
import static com.predic8.membrane.core.util.wsdl.parser.Operation.Direction.INPUT;
import static com.predic8.membrane.core.util.wsdl.parser.Operation.Direction.OUTPUT;

/**
 * Generates an OpenAPI 3.1 model from WSDL definitions.
 */
public class Wsdl2OpenApiConverter {

    private static final Logger log = LoggerFactory.getLogger(Wsdl2OpenApiConverter.class);

    /** Mapping of an operation the configuration says nothing about: POST on its kebab-case name. */
    private static final OperationSettings DEFAULT_SETTINGS = new OperationSettings();

    /**
     * Problem details subtype for a failed operation. Deliberately says nothing about SOAP: the
     * {@code type} URI reaches the client, and an operator may not want to advertise that a legacy
     * service sits behind the API.
     */
    static final String OPERATION_ERROR_TYPE = "operation-error";

    /** The problem details member carrying the content of a fault the operation declares. */
    static final String FAULT_DETAILS_FIELD = "details";

    /** What {@code info.version} says when the plugin is not configured with one. */
    static final String DEFAULT_VERSION = "1.0.0";

    private static final String PROBLEM_DETAILS_SCHEMA = "ProblemDetails";
    private static final String PROBLEM_DETAILS_REF = "#/components/schemas/" + PROBLEM_DETAILS_SCHEMA;

    private final Definitions definitions;
    private final String basePath;
    private final XsdToSchema converter;
    private final Map<String, OperationSettings> operations;
    private final ApiInfo info;

    /**
     * What the generated document's {@code info} block says, where the plugin is configured with it.
     * A {@code null} member falls back to what the WSDL says — see {@link #buildInfo()}.
     */
    record ApiInfo(String title, String description, String version) {
        /** Nothing configured: every value comes from the WSDL. */
        static final ApiInfo NONE = new ApiInfo(null, null, null);
    }

    /**
     * Per operation, the URL parameters published under a name other than the body property they
     * fill. Filled while the paths are built; see {@link #publishedName}.
     */
    private final Map<String, Map<String, String>> urlParamProperties = new LinkedHashMap<>();

    public Wsdl2OpenApiConverter(Definitions definitions, String basePath,
                                 Map<String, OperationSettings> operations, ApiInfo info) {
        this.definitions = definitions;
        this.basePath = stripTrailingSlash(basePath);
        this.converter = new XsdToSchema(definitions, Set.of(PROBLEM_DETAILS_SCHEMA));
        this.operations = operations;
        this.info = info;
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

    /**
     * The converter that produced the document's schemas, so a caller converting messages at runtime
     * resolves the same components the document publishes.
     */
    XsdToSchema getSchemaConverter() {
        return converter;
    }

    /**
     * Per operation id, the body property each URL parameter fills, for the parameters whose
     * published name differs from it. Only meaningful once the document has been generated: building
     * the paths is what decides how the input fields are carried.
     */
    Map<String, Map<String, String>> getUrlParamProperties() {
        return Map.copyOf(urlParamProperties);
    }

    public OpenAPI generate() {
        var openAPI = new OpenAPI();
        openAPI.setOpenapi("3.1.2");
        openAPI.setInfo(buildInfo());
        openAPI.setServers(List.of(new Server().url(basePath)));
        // Must stay after buildPaths(): converting the paths is what discovers the named XSD types
        // the components below are collected from.
        openAPI.setPaths(buildPaths());
        openAPI.setComponents(buildComponents());
        var topLevelTags = buildTopLevelTags(openAPI.getPaths());
        if (!topLevelTags.isEmpty()) {
            openAPI.setTags(topLevelTags);
        }
        return openAPI;
    }

    /** The error shape every operation refers to, plus one entry per named XSD type in use. */
    private Components buildComponents() {
        var components = new Components().addSchemas(PROBLEM_DETAILS_SCHEMA, buildProblemDetailsSchema());
        converter.getComponents().forEach(components::addSchemas);
        return components;
    }

    /** RFC 7807 problem details, the shape of every error response this API returns. */
    private static Schema<?> buildProblemDetailsSchema() {
        return new ObjectSchema()
                .description("Problem details as defined by RFC 7807.")
                .addProperty("type", new StringSchema().description("Identifies the kind of problem."))
                .addProperty("title", new StringSchema().description("Short summary of the problem."))
                .addProperty("status", new IntegerSchema().description("The HTTP status code."))
                .addProperty("detail", new StringSchema().description("Explanation specific to this occurrence."))
                .addProperty("instance", new StringSchema().description("Identifies this specific occurrence."));
    }

    /**
     * Declares the tags the operations carry, in the order the paths use them. Read from the built
     * paths rather than from the configuration, so the tag an unconfigured operation falls back to
     * is declared too.
     */
    private static List<Tag> buildTopLevelTags(Paths paths) {
        return paths.values().stream()
                .flatMap(pathItem -> pathItem.readOperations().stream())
                .filter(op -> op.getTags() != null)
                .flatMap(op -> op.getTags().stream())
                .distinct()
                .map(name -> new Tag().name(name))
                .toList();
    }

    /**
     * The document as YAML. Serialized by the OpenAPI 3.1 writer rather than a plain object mapper:
     * that one writes a schema's vendor extensions as the {@code x-} members they are and leaves the
     * model's internal bookkeeping — {@code exampleSetFlag}, the {@code types} list backing
     * {@code type} — out of the published document.
     */
    public String generateYaml() {
        return Yaml31.pretty(generate());
    }

    /** CommonMark, per the OpenAPI spec — rendered as-is by Swagger UI, Redoc, etc. */
    private static final String DESCRIPTION = """
            ![Logo](https://raw.githubusercontent.com/membrane/api-gateway/master/docs/images/membrane-logo-128.png)

            Auto-generated OpenAPI from WSDL by \
            [Membrane](https://www.membrane-api.io/?oas=1), \
            which turns legacy SOAP/WSDL services into modern REST APIs. \
            Membrane is a modern and lightweight [open source API Gateway](https://github.com/membrane/api-gateway).""";

    private Info buildInfo() {
        return new Info()
                .title(info.title() != null ? info.title() : getServiceName())
                .description(infoDescription())
                .version(info.version() != null ? info.version() : DEFAULT_VERSION);
    }

    /**
     * What the document says about the API as a whole: the configured description where there is one,
     * otherwise what the WSDL documents its service — or, where the service documents nothing, its
     * definitions — with. The generated note about Membrane follows below it.
     */
    private String infoDescription() {
        String text = info.description() != null ? info.description() : wsdlDocumentation();
        return text != null ? text + "\n\n" + DESCRIPTION : DESCRIPTION;
    }

    private String wsdlDocumentation() {
        return definitions.getServices().stream()
                .map(Service::getDocumentation)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseGet(definitions::getDocumentation);
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
        for (var wsdlOp : definitions.getOperations()) {
            if (wsdlOp.getName() == null) {
                log.debug("Skipping WSDL operation with null name");
                continue;
            }
            named.add(wsdlOp);
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
        var headerParts = definitions.findBindingOperation(name).map(this::getHeaderParts).orElse(List.of());
        warnAboutHeaderParts(name, headerParts);

        // No summary: it could only repeat the operation name, which operationId and the path already
        // carry. What the WSDL documents the operation with goes into description instead.
        var apiOp = new io.swagger.v3.oas.models.Operation()
                .operationId(name)
                .description(wsdlOp.getDocumentation())
                .responses(buildResponses(wsdlOp));

        // Every operation is tagged: an untagged one would end up in the "default" group of a
        // documentation UI, and with no tag configured anywhere that is where all of them land.
        apiOp.addTagsItem(settings.getTag() != null ? settings.getTag() : getServiceName());

        List<String> pathParamNames = settings.getPath() == null ? List.of() : extractParamNames(settings.getPath());
        Schema<?> bodySchema = converter.convertParts(getBodyParts(inputParts, headerParts));

        // The path parameters take over the schema of the input fields they carry, and those fields
        // leave the body: the client puts them in the URL and Wsdl2OpenapiInterceptor merges them
        // back into the JSON before the SOAP transformation, so asking for them twice would make a
        // validator reject a correct request.
        Map<String, String> pathProperties = new LinkedHashMap<>();
        pathParamNames.forEach(p -> pathProperties.put(p, resolveProperty(bodySchema, p)));

        Map<String, Schema> paramSchemas = removeProperties(bodySchema, List.copyOf(pathProperties.values()));
        pathProperties.forEach((paramName, property) -> {
            Schema<?> paramSchema = paramSchemas.getOrDefault(property, new StringSchema());
            requirePathCarriable(name, paramName, paramSchema);
            recordUrlParam(name, paramName, property);
            apiOp.addParametersItem(new Parameter().name(paramName).in("path").required(true)
                    .schema(paramSchema));
        });
        if (hasRequestBody(settings.getMethod())) {
            if (!isEmptySchema(bodySchema)) {
                apiOp.requestBody(buildRequestBody(bodySchema));
            }
        } else {
            addQueryParameters(apiOp, name, settings.getMethod(), bodySchema);
        }
        return apiOp;
    }

    /**
     * Tells the operator about the SOAP header parts of an operation, which this plugin does not
     * support: nothing puts a value into the header of the SOAP request it builds. They are left out
     * of the document rather than published as header parameters — a parameter the gateway silently
     * discards is worse than one the document never promised.
     */
    private static void warnAboutHeaderParts(String operationName, List<Part> headerParts) {
        if (headerParts.isEmpty()) return;
        log.warn("Operation '{}' declares the SOAP header part(s) {}, which are not supported: they are left "
                 + "out of the generated OpenAPI document and are not sent to the service.",
                operationName, headerParts.stream().map(Part::getName).toList());
    }

    /**
     * Whether the operation's input carries nothing: the message has no parts, or the parts resolve
     * to an empty complex type. Such an operation gets no request body — demanding one would make a
     * validator reject the empty request a client correctly sends. A reference is never empty: it
     * names a type the document declares elsewhere.
     */
    private static boolean isEmptySchema(Schema<?> schema) {
        return schema.get$ref() == null
               && (schema.getProperties() == null || schema.getProperties().isEmpty());
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

    /**
     * The message parts a binding carries in the SOAP header of the operation's input. Not published
     * as parameters — see {@link #warnAboutHeaderParts} — but needed to keep them out of the request
     * body, which is not where the service expects them.
     */
    private List<Part> getHeaderParts(BindingOperation bindingOperation) {
        return bindingOperation.getInputs().stream()
                .flatMap(input -> input.getHeaders().stream())
                .map(this::resolveHeaderPart)
                .filter(Objects::nonNull)
                .toList();
    }

    private Part resolveHeaderPart(SoapHeader header) {
        Optional<Part> part = definitions.findMessage(WSDLParserUtil.getLocalName(header.getMessage()))
                .flatMap(message -> message.getParts().stream()
                        .filter(p -> header.getPart().equals(p.getName()))
                        .findFirst());
        if (part.isEmpty()) {
            log.debug("soap:header part '{}' of message '{}' could not be resolved, skipping",
                    header.getPart(), header.getMessage());
        }
        return part.orElse(null);
    }

    /**
     * Carries the input fields a bodyless method has no other place for: everything the path template
     * did not take over becomes a query parameter. Without this the fields would be absent from the
     * document and from the SOAP request, and neither the client nor the operator would be told.
     */
    private void addQueryParameters(io.swagger.v3.oas.models.Operation apiOp, String operationName,
                                    String method, Schema<?> bodySchema) {
        if (bodySchema.getProperties() == null) return;
        List<String> required = bodySchema.getRequired() != null ? bodySchema.getRequired() : List.of();
        bodySchema.getProperties().forEach((fieldName, fieldSchema) -> {
            // A field of a named XSD type is a reference; whether it fits into a query parameter is
            // a property of the type it names, so the reference has to be followed to tell.
            if (!isScalar(deref(fieldSchema))) {
                throw new ConfigurationException("""
                        Operation '%s' is mapped to %s, which has no request body, but its input field '%s' is \
                        a %s and cannot be carried as a query parameter.
                        Map the operation to POST, or name the field in the path template.""".formatted(
                        operationName, method, fieldName, describe(deref(fieldSchema))));
            }
            String paramName = publishedName(operationName, fieldName, bodySchema);
            recordUrlParam(operationName, paramName, fieldName);
            apiOp.addParametersItem(new Parameter().name(paramName).in("query")
                    .required(required.contains(fieldName)).schema(fieldSchema));
        });
    }

    /**
     * Rejects a path template naming an input field that a path segment cannot carry. A segment holds
     * a single value, and {@link Wsdl2OpenapiInterceptor} merges it back into the JSON as one, so a
     * complex or repeating field published this way would yield a request the service rejects — and a
     * parameter no client can serialize in the first place.
     */
    private void requirePathCarriable(String operationName, String paramName, Schema<?> paramSchema) {
        if (isScalar(deref(paramSchema))) return;
        throw new ConfigurationException("""
                Operation '%s' has a path template naming '%s', but that input field is a %s and cannot be \
                carried in a path segment.
                Remove it from the path template, and map the operation to POST so that the field is carried \
                in the request body.""".formatted(operationName, paramName, describe(deref(paramSchema))));
    }

    /**
     * The name a URL parameter carrying {@code property} is published under. An XSD attribute is a
     * property named {@code "@"} + its name, and an {@code @} in a parameter name has to be
     * percent-encoded by every client, so an attribute is published under its plain name and
     * {@link Wsdl2OpenapiInterceptor} puts the prefix back before the SOAP transformation.
     */
    private static String publishedName(String operationName, String property, Schema<?> bodySchema) {
        if (!property.startsWith(ATTRIBUTE_PREFIX)) return property;
        String plain = property.substring(ATTRIBUTE_PREFIX.length());
        if (bodySchema.getProperties().containsKey(plain)) {
            throw new ConfigurationException("""
                    Operation '%s' has both an element '%s' and an attribute '%s' in its input, which \
                    would have to share the URL parameter name '%s'.
                    Map the operation to POST, so that both are carried in the request body.""".formatted(
                    operationName, plain, plain, plain));
        }
        return plain;
    }

    /**
     * The body property a URL parameter named {@code paramName} fills: the property of that name, or
     * the attribute it stands for where the input declares no element of that name (see
     * {@link #publishedName}).
     */
    private static String resolveProperty(Schema<?> bodySchema, String paramName) {
        if (bodySchema.getProperties() == null || bodySchema.getProperties().containsKey(paramName)) return paramName;
        String attribute = ATTRIBUTE_PREFIX + paramName;
        return bodySchema.getProperties().containsKey(attribute) ? attribute : paramName;
    }

    /** Notes a URL parameter published under a name other than the body property it fills. */
    private void recordUrlParam(String operationName, String paramName, String property) {
        if (paramName.equals(property)) return;
        urlParamProperties.computeIfAbsent(operationName, op -> new LinkedHashMap<>()).put(paramName, property);
    }

    /**
     * The component {@code schema} references — see {@link XsdDomUtil#dereference}. Used to inspect a
     * schema's type; the reference itself is what stays in the document.
     */
    private Schema<?> deref(Schema<?> schema) {
        return dereference(converter.getComponents(), schema);
    }

    /** Only a single value fits into a query parameter the interceptor can put back into the JSON. */
    private static boolean isScalar(Schema<?> schema) {
        return switch (schema.getType()) {
            case "string", "integer", "number", "boolean" -> true;
            case null, default -> false;
        };
    }

    private static String describe(Schema<?> schema) {
        return switch (schema.getType()) {
            case "array" -> "repeating field";
            case null -> "field of unknown type";
            default -> "complex type";
        };
    }

    private RequestBody buildRequestBody(Schema<?> schema) {
        return new RequestBody()
                .required(true)
                .content(jsonContent(schema));
    }

    /**
     * Removes the named properties from the given schema and returns them, keyed by name. A name the
     * schema has no property for is absent from the result — the WSDL says nothing about that field,
     * so the caller decides what to do with it.
     */
    private static Map<String, Schema> removeProperties(Schema<?> schema, List<String> names) {
        if (names.isEmpty() || schema.getProperties() == null) return Map.of();
        var removed = new LinkedHashMap<String, Schema>();
        for (String name : names) {
            Schema<?> property = schema.getProperties().remove(name);
            if (property != null) removed.put(name, property);
        }
        if (schema.getRequired() != null) {
            schema.getRequired().removeAll(names);
            if (schema.getRequired().isEmpty()) schema.setRequired(null);
        }
        return removed;
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
                .addApiResponse(ApiResponses.DEFAULT, buildErrorResponse(wsdlOp));
    }

    /**
     * The one error response. Every error the gateway produces — a fault from the service, a request
     * it could not transform, a method the path does not support — is an RFC 7807 problem details
     * document, so a single {@code default} response describes them all. Their statuses differ: a
     * fault becomes a 500, a request that cannot be mapped a 400, an unsupported method a 405.
     * Enumerating them here would still be guesswork, because other plugins in the API's flow have
     * statuses of their own — which is exactly what {@code default} is for.
     */
    private ApiResponse buildErrorResponse(Operation wsdlOp) {
        return new ApiResponse()
                .description(errorDescription(wsdlOp))
                .content(new Content().addMediaType(APPLICATION_PROBLEM_JSON,
                        new MediaType().schema(errorSchema(wsdlOp))));
    }

    /**
     * The problem details schema, extended with the operation's declared faults under
     * {@code details} when it has any. Only one fault can be present in a response, so the
     * alternatives are combined with {@code oneOf}, each wrapped in the single-property object
     * the runtime emits.
     */
    private Schema<?> errorSchema(Operation wsdlOp) {
        List<Schema> faults = wsdlOp.getFaults().stream()
                .map(fault -> fault.getMessage().getParts())
                .filter(parts -> !parts.isEmpty())
                .map(parts -> (Schema) new ObjectSchema()
                        .addProperty(XsdToSchema.faultDetailKey(parts), converter.convertParts(parts)))
                .toList();

        if (faults.isEmpty()) {
            return problemDetailsRef();
        }
        Schema<?> details = faults.size() == 1 ? faults.getFirst() : new ComposedSchema().oneOf(faults);
        return new ComposedSchema().allOf(List.of(
                problemDetailsRef(),
                new ObjectSchema().addProperty(FAULT_DETAILS_FIELD, details)));
    }

    private static Schema<?> problemDetailsRef() {
        return new Schema<>().$ref(PROBLEM_DETAILS_REF);
    }

    private String errorDescription(Operation wsdlOp) {
        var text = new StringBuilder("An error occurred. The response is a problem details document (RFC 7807).");
        List<String> faultNames = wsdlOp.getFaults().stream()
                .map(fault -> fault.getMessage().getParts())
                .filter(parts -> !parts.isEmpty())
                .map(XsdToSchema::faultDetailKey)
                .toList();
        if (!faultNames.isEmpty()) {
            text.append(" When the service reports one of the errors this operation declares (")
                .append(String.join(", ", faultNames))
                .append("), its content appears under `").append(FAULT_DETAILS_FIELD).append("`.");
        }
        return text.toString();
    }

    private String getServiceName() {
        List<Service> services = definitions.getServices();
        if (!services.isEmpty()) {
            return services.getFirst().getName();
        }
        return "SOAP Service";
    }

}
