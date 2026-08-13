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

import com.predic8.membrane.core.resolver.ResolverMap;
import com.predic8.membrane.core.util.ConfigurationException;
import com.predic8.membrane.core.util.wsdl.parser.Definitions;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.tags.Tag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.interceptor.wsdl2openapi.XsdDomUtil.componentName;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class OpenApiGeneratorTest {

    static Definitions citiesDefinitions;
    static Definitions blzDefinitions;
    static Definitions extendedDefinitions;
    static Definitions crossNsDefinitions;
    static Definitions recursiveDefinitions;
    static Definitions articleDefinitions;
    static Definitions attributeDefinitions;

    @BeforeAll
    static void setup() throws Exception {
        citiesDefinitions = Definitions.parse(new ResolverMap(), "classpath:/ws/cities.wsdl");
        blzDefinitions = Definitions.parse(new ResolverMap(), "classpath:/blz-service.wsdl");
        extendedDefinitions = Definitions.parse(new ResolverMap(), "classpath:/ws/extended-types.wsdl");
        crossNsDefinitions = Definitions.parse(new ResolverMap(), "classpath:/ws/cross-namespace.wsdl");
        recursiveDefinitions = Definitions.parse(new ResolverMap(), "classpath:/ws/recursive-type.wsdl");
        articleDefinitions = Definitions.parse(new ResolverMap(), "classpath:/validation/article-service.wsdl");
        attributeDefinitions = Definitions.parse(new ResolverMap(), "classpath:/ws/attributes.wsdl");
    }

    @Test
    void openApiHeader() {
        var yaml = generator(citiesDefinitions, "/purchasing");

        assertTrue(yaml.contains("openapi: 3.1.2"), "Should contain openapi version");
        assertTrue(yaml.contains("info:"), "Should contain info section");
        assertTrue(yaml.contains("version: 1.0.0"), "Should contain version");
    }

    @Test
    void serviceNameUsedAsTitle() {
        assertTrue(generator(citiesDefinitions, "/purchasing").contains("title: CityService"));
    }

    @Test
    void serverUrlFromBasePath() {
        assertTrue(generator(citiesDefinitions, "/purchasing").contains("url: /purchasing"));
    }

    @Test
    void basePathTrailingSlashStripped() {
        var yaml = new Wsdl2OpenApiConverter(citiesDefinitions, "/purchasing/").generateYaml();

        assertTrue(yaml.contains("url: /purchasing"));
        assertFalse(yaml.contains("url: /purchasing/"));
    }

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource
    void stripTrailingSlash(String basePath, String expected) {
        assertEquals(expected, Wsdl2OpenApiConverter.stripTrailingSlash(basePath));
    }

    static Stream<Arguments> stripTrailingSlash() {
        return Stream.of(
                arguments("/purchasing/", "/purchasing"),
                arguments("/purchasing",  "/purchasing"),   // nothing to strip
                arguments("/",            "/"),             // root path is kept, not emptied
                arguments("/a//",         "/a/"),           // only one slash is stripped
                arguments("",             "")               // no path at all: left alone
        );
    }

    @Test
    void rootBasePathStaysASlash() {
        // An api without a path yields basePath "/". Stripping that to "" would leave an empty
        // server url, which Swagger UI resolves against the /api-docs page, not the API root.
        assertTrue(generator(citiesDefinitions, "/").contains("url: /"));
    }

    @Test
    void camelCaseOperationMappedToKebabPath() {
        assertTrue(generator(citiesDefinitions, "/").contains("/get-city:"));
    }

    @Test
    void operationUsesPostMethod() {
        var yaml = generator(citiesDefinitions, "/");

        assertTrue(yaml.contains("post:"));
        assertFalse(yaml.contains("get:"));
    }

    @Test
    void operationIdMatchesOriginalCamelCase() {
        assertTrue(generator(citiesDefinitions, "/").contains("operationId: getCity"));
    }

    @Test
    void requestBodyIsRequired() {
        var yaml = generator(citiesDefinitions, "/");

        assertTrue(yaml.contains("required: true"));
        assertTrue(yaml.contains("application/json:"));
    }

    @Test
    void responsesWith200AndDefaultProblemDetails() {
        var yaml = generator(citiesDefinitions, "/");

        assertTrue(yaml.contains("\"200\":"));
        assertTrue(yaml.contains("default:"));
        assertFalse(yaml.contains("\"500\":"), "Errors are described by the default response, not by an explicit status");
        assertTrue(yaml.contains("application/problem+json:"));
        assertTrue(yaml.contains("$ref: \"#/components/schemas/ProblemDetails\""));
    }

    @Test
    void problemDetailsSchemaIsDeclaredOnceAndReferenced() {
        var yaml = generator(citiesDefinitions, "/");

        assertEquals(1, yaml.split("ProblemDetails:").length - 1,
                "declared once as a component, referenced by every operation");
        assertTrue(yaml.contains("Problem details as defined by RFC 7807."));
    }

    @Test
    void operationWithoutDeclaredFaultsHasNoDetailsMember() {
        // cities.wsdl declares no wsdl:fault, so the error response is the bare problem details
        // document — there is no operation-specific fault content to describe.
        var yaml = generator(citiesDefinitions, "/");

        assertFalse(yaml.contains("details:"), "Nothing to put under details when no fault is declared");
        assertFalse(yaml.contains("allOf:"));
    }

    @Test
    void allPortTypeOperationsAreGenerated() {
        assertTrue(new Wsdl2OpenApiConverter(citiesDefinitions, "/").generateYaml().contains("/get-city:"));
        assertTrue(new Wsdl2OpenApiConverter(blzDefinitions, "/blz").generateYaml().contains("/get-bank:"));
    }

    @Test
    void blzServiceTitle() {
        var yaml = generator(blzDefinitions, "/blz");

        assertTrue(yaml.contains("title: BLZService"));
        assertTrue(yaml.contains("/get-bank:"));
    }

    @Test
    void citiesProducesGetCityPath() {
        var yaml = new Wsdl2OpenApiConverter(citiesDefinitions, "/").generateYaml();

        assertTrue(yaml.contains("paths:"));
        assertTrue(yaml.contains("/get-city:"));
    }

    @Test
    void getCityRequestHasNameField() {
        assertTrue(generator(citiesDefinitions, "/").contains("name:"));
    }

    @Test
    void getCityResponseHasCountryAndPopulation() {
        var yaml = generator(citiesDefinitions, "/");

        assertTrue(yaml.contains("country:"));
        assertTrue(yaml.contains("population:"));
    }

    @Test
    void getBankRequestHasBLZField() {
        assertTrue(generator(blzDefinitions, "/blz").contains("blz:"));
    }

    @Test
    void getBankResponseHasDetailsObject() {
        var yaml = generator(blzDefinitions, "/blz");

        assertTrue(yaml.contains("details:"));
        assertTrue(yaml.contains("bezeichnung:"));
    }

    @Test
    void extensionInheritsBaseTypeFields() {
        var yaml = generator(extendedDefinitions, "/");

        assertTrue(yaml.contains("baseId:"), "Should contain 'baseId' inherited from BaseType");
        assertTrue(yaml.contains("baseName:"), "Should contain 'baseName' inherited from BaseType");
        assertTrue(yaml.contains("extra:"), "Should contain 'extra' from ExtendedType");
    }

    @Test
    void unboundedElementProducesArraySchema() {
        assertTrue(generator(extendedDefinitions, "/").contains("type: array"));
    }

    @Test
    void choiceAlternativesAreAllPresent() {
        // searchRequest has <xsd:choice> with byName and byId
        var yaml = generator(extendedDefinitions, "/");

        assertTrue(yaml.contains("byName:"), "Should contain 'byName' from choice");
        assertTrue(yaml.contains("byId:"), "Should contain 'byId' from choice");
        assertTrue(yaml.contains("oneOf:"), "Should state that exactly one alternative is expected");
    }

    @Test
    void namedSimpleTypeRestrictedToStringMapsToString() {
        assertTrue(generator(extendedDefinitions, "/").contains("code:"));
    }

    @Test
    void crossNamespaceTypeIsResolved() {
        var yaml = generator(crossNsDefinitions, "/");

        assertTrue(yaml.contains("itemName:"), "Should resolve ItemType from the types namespace");
        assertTrue(yaml.contains("itemCount:"), "Should resolve ItemType from the types namespace");
    }

    @Test
    void aNamedTypeIsWrittenOutOnceHoweverManyOperationsUseIt() {
        // ItemType types both the request's 'item' and the response's 'result'. Inlining it at each
        // use site is what makes a document of a real WSDL unnavigable.
        var yaml = generator(crossNsDefinitions, "/");

        assertEquals(1, yaml.split("itemName:").length - 1, yaml);
        assertEquals(1, yaml.split("ItemType:").length - 1, "declared once, under components");
    }

    @Test
    void aFieldOfANamedTypeIsReachableFromTheOperationThroughItsComponent() {
        var api = new Wsdl2OpenApiConverter(crossNsDefinitions, "/").generate();

        var requestBody = api.getPaths().get("/get-item").getPost().getRequestBody()
                .getContent().get(APPLICATION_JSON).getSchema();
        Schema<?> item = (Schema<?>) requestBody.getProperties().get("item");

        // The substring assertions elsewhere would also pass if a reference pointed nowhere; this
        // walks the way a client or a generator does.
        assertEquals("#/components/schemas/ItemType", item.get$ref());
        var itemType = api.getComponents().getSchemas().get(componentName(item.get$ref()));
        assertNotNull(itemType, "the operation references a component the document does not declare");
        assertInstanceOf(StringSchema.class, itemType.getProperties().get("itemName"));
        assertInstanceOf(IntegerSchema.class, itemType.getProperties().get("itemCount"));
    }

    @Test
    void selfReferencingTypeDoesNotCauseStackOverflow() {
        assertDoesNotThrow(() -> generator(recursiveDefinitions, "/"),
                "Self-referencing complexType must not cause StackOverflowError");
    }

    @Test
    void selfReferencingTypePreservesNonRecursiveFields() {
        var yaml = generator(recursiveDefinitions, "/");
        assertTrue(yaml.contains("value:"), "Non-recursive 'value' field should be present");
    }

    @Test
    void externalXsdImportChainResolvesMoneyType() {
        var yaml = generator(articleDefinitions, "/");
        assertTrue(yaml.contains("amount:"), "MoneyType.amount should be resolved from external XSD chain");
        assertTrue(yaml.contains("currency:"), "MoneyType.currency should be resolved from external XSD chain");
    }

    @Test
    void operationTagAppearsInYaml() {
        var settings = new OperationSettings();
        settings.setTag("MyService");
        var ops = Map.of("getCity", settings);
        var yaml = new Wsdl2OpenApiConverter(citiesDefinitions, "/", ops).generateYaml();

        assertTrue(yaml.contains("tags:"), "Should contain tags section");
        assertTrue(yaml.contains("MyService"), "Should contain configured tag value");
        assertTrue(yaml.contains("- name: MyService"), "Top-level tags section should declare the tag by name");
    }

    @Test
    void configuredOperationNotInWsdlIsRejected() {
        var ops = Map.of("getCitty", new OperationSettings());

        var e = assertThrows(ConfigurationException.class,
                () -> new Wsdl2OpenApiConverter(citiesDefinitions, "/", ops).generate());

        assertTrue(e.getMessage().contains("getCitty"), "Message should name the unknown operation");
        assertTrue(e.getMessage().contains("getCity"), "Message should list the available operations");
    }

    @Test
    void pathParameterFieldIsRemovedFromRequestBody() {
        // getCity's only input field is carried by the path, so nothing is left for a body.
        assertNull(pathMappedGetCity().getPaths().get("/cities/{name}").getPut().getRequestBody(),
                "Field bound to the path parameter must not be repeated in the body");
    }

    @Test
    void fieldsThePathDoesNotCarryStayInTheRequestBody() {
        // search declares byName, byId and code; only byId is bound to the path.
        var settings = new OperationSettings();
        settings.setPath("/search/{byId}");
        settings.setMethod("PUT");
        var body = new Wsdl2OpenApiConverter(extendedDefinitions, "/", Map.of("search", settings)).generate()
                .getPaths().get("/search/{byId}").getPut()
                .getRequestBody().getContent().get(APPLICATION_JSON).getSchema();

        assertNull(body.getProperties().get("byId"), "the path parameter must not be repeated in the body");
        assertNotNull(body.getProperties().get("byName"));
    }

    @Test
    void pathParameterIsStillDeclaredAsParameter() {
        var op = pathMappedGetCity().getPaths().get("/cities/{name}").getPut();

        assertEquals(1, op.getParameters().size());
        assertEquals("name", op.getParameters().getFirst().getName());
        assertEquals("path", op.getParameters().getFirst().getIn());
    }

    @Test
    void pathParameterTakesTheTypeOfTheInputField() {
        // searchRequest declares byId as xsd:int, so the parameter must not degrade to a string.
        var settings = new OperationSettings();
        settings.setPath("/search/{byId}");
        settings.setMethod("GET");
        var api = new Wsdl2OpenApiConverter(extendedDefinitions, "/", Map.of("search", settings)).generate();

        var parameter = api.getPaths().get("/search/{byId}").getGet().getParameters().getFirst();
        assertEquals("byId", parameter.getName());
        assertEquals("integer", parameter.getSchema().getType());
    }

    @Test
    void pathParameterWithoutMatchingInputFieldFallsBackToString() {
        var settings = new OperationSettings();
        settings.setPath("/cities/{unknown}");
        settings.setMethod("GET");
        var api = new Wsdl2OpenApiConverter(citiesDefinitions, "/", Map.of("getCity", settings)).generate();

        assertEquals("string", api.getPaths().get("/cities/{unknown}").getGet()
                .getParameters().getFirst().getSchema().getType());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"GET", "DELETE"})
    void inputFieldsNotInThePathBecomeQueryParameters(String method) {
        // search declares byName, byId and code; only byId is carried by the path.
        var op = searchMappedTo(method, "/search/{byId}").getPaths().get("/search/{byId}")
                .readOperations().getFirst();

        assertNull(op.getRequestBody(), "%s must not declare a request body".formatted(method));
        var query = op.getParameters().stream().filter(p -> "query".equals(p.getIn())).toList();
        assertEquals(List.of("byName", "code"), query.stream().map(Parameter::getName).toList());
        assertEquals("string", query.getFirst().getSchema().getType());
    }

    @Test
    void queryParameterIsRequiredWhenTheInputFieldIs() {
        // getCity declares a mandatory name; search's code is minOccurs="0".
        var settings = new OperationSettings();
        settings.setMethod("GET");
        var mandatory = new Wsdl2OpenApiConverter(citiesDefinitions, "/", Map.of("getCity", settings)).generate()
                .getPaths().get("/get-city").getGet().getParameters().getFirst();
        assertEquals("name", mandatory.getName());
        assertTrue(mandatory.getRequired());

        var optional = byName(queryParametersOf(searchMappedTo("GET", "/search/{byId}"), "/search/{byId}"), "code");
        assertFalse(optional.getRequired());
    }

    @Test
    void postKeepsTheLeftoverFieldsInTheBody() {
        var op = searchMappedTo("POST", "/search/{byId}").getPaths().get("/search/{byId}").getPost();

        assertTrue(op.getParameters().stream().noneMatch(p -> "query".equals(p.getIn())));
        assertNotNull(op.getRequestBody().getContent().get("application/json").getSchema()
                .getProperties().get("byName"));
    }

    @Test
    void complexInputFieldCannotBeCarriedByABodylessMethod() {
        // getItem's only input field is an ItemType structure, so a GET has nowhere to put it.
        var settings = new OperationSettings();
        settings.setMethod("GET");
        var e = assertThrows(ConfigurationException.class,
                () -> new Wsdl2OpenApiConverter(crossNsDefinitions, "/", Map.of("getItem", settings)).generate());

        assertTrue(e.getMessage().contains("getItem"), "Message should name the operation");
        assertTrue(e.getMessage().contains("item"), "Message should name the field that cannot be carried");
    }

    @Test
    void complexInputFieldCannotBeCarriedByThePath() {
        // getItem's only input field is an ItemType structure: a path segment holds a single value.
        var settings = new OperationSettings();
        settings.setPath("items/{item}");
        var e = assertThrows(ConfigurationException.class,
                () -> new Wsdl2OpenApiConverter(crossNsDefinitions, "/", Map.of("getItem", settings)).generate());

        assertTrue(e.getMessage().contains("getItem"), "Message should name the operation");
        assertTrue(e.getMessage().contains("'item'"), "Message should name the parameter that cannot be carried");
    }

    @Test
    void anAttributeBecomesAQueryParameterWithoutTheAtPrefix() {
        // record's input carries the attributes id and type; "@id" would have to be sent as %40id.
        var settings = new OperationSettings();
        settings.setMethod("GET");
        var op = new Wsdl2OpenApiConverter(attributeDefinitions, "/", Map.of("record", settings)).generate()
                .getPaths().get("/record").getGet();

        assertEquals(List.of("name", "id", "type"),
                op.getParameters().stream().map(Parameter::getName).toList());
    }

    @Test
    void anAttributeCanBeCarriedByThePathParameter() {
        var settings = new OperationSettings();
        settings.setPath("/records/{id}");
        settings.setMethod("GET");
        var converter = new Wsdl2OpenApiConverter(attributeDefinitions, "/", Map.of("record", settings));
        var op = converter.generate().getPaths().get("/records/{id}").getGet();

        assertEquals("path", byName(op.getParameters(), "id").getIn());
        // The remaining attribute travels as a query parameter, under its plain name as well.
        assertEquals(Map.of("record", Map.of("id", "@id", "type", "@type")), converter.getUrlParamProperties(),
                "the interceptor must be told which body property each parameter fills");
    }

    @Test
    void anAttributeClashingWithAnElementOfTheSameNameIsRejected() {
        // clash's input declares both an element id and an attribute id: one URL name, two fields.
        var settings = new OperationSettings();
        settings.setMethod("GET");
        var e = assertThrows(ConfigurationException.class,
                () -> new Wsdl2OpenApiConverter(attributeDefinitions, "/", Map.of("clash", settings)).generate());

        assertTrue(e.getMessage().contains("clash"), "Message should name the operation");
        assertTrue(e.getMessage().contains("'id'"), "Message should name the contested parameter");
    }

    private static OpenAPI searchMappedTo(String method, String path) {
        var settings = new OperationSettings();
        settings.setPath(path);
        settings.setMethod(method);
        return new Wsdl2OpenApiConverter(extendedDefinitions, "/", Map.of("search", settings)).generate();
    }

    private static List<Parameter> queryParametersOf(OpenAPI api, String path) {
        return api.getPaths().get(path).readOperations().getFirst().getParameters().stream()
                .filter(p -> "query".equals(p.getIn())).toList();
    }

    private static Parameter byName(List<Parameter> parameters, String name) {
        return parameters.stream().filter(p -> name.equals(p.getName())).findFirst().orElseThrow();
    }

    private static OpenAPI pathMappedGetCity() {
        var settings = new OperationSettings();
        settings.setPath("/cities/{name}");
        settings.setMethod("PUT");
        return new Wsdl2OpenApiConverter(citiesDefinitions, "/", Map.of("getCity", settings)).generate();
    }

    @Test
    void unconfiguredOperationIsTaggedWithTheServiceName() {
        var api = new Wsdl2OpenApiConverter(citiesDefinitions, "/").generate();

        assertEquals(List.of("CityService"), api.getPaths().get("/get-city").getPost().getTags(),
                "without a configured tag every operation would land in the 'default' group of a UI");
        assertEquals(List.of("CityService"), api.getTags().stream().map(Tag::getName).toList(),
                "the fallback tag is declared at the top level too");
    }

    @Test
    void configuredTagWinsOverTheServiceName() {
        var settings = new OperationSettings();
        settings.setTag("MyService");
        var api = new Wsdl2OpenApiConverter(citiesDefinitions, "/", Map.of("getCity", settings)).generate();

        assertEquals(List.of("MyService"), api.getPaths().get("/get-city").getPost().getTags());
        assertEquals(List.of("MyService"), api.getTags().stream().map(Tag::getName).toList());
    }

    @Test
    void configuredVersionReplacesTheDefault() {
        var api = new Wsdl2OpenApiConverter(citiesDefinitions, "/", Map.of(), null, null, "2.1.0").generate();

        assertEquals("2.1.0", api.getInfo().getVersion());
    }

    @Test
    void versionFallsBackToTheDefaultWhenNotConfigured() {
        assertEquals(Wsdl2OpenApiConverter.DEFAULT_VERSION,
                new Wsdl2OpenApiConverter(citiesDefinitions, "/", Map.of(), null, null, null).generate()
                        .getInfo().getVersion());
    }

    // ── wsdl:documentation / xsd:documentation ────────────────────────────

    @Test
    void serviceDocumentationBecomesInfoDescription() throws Exception {
        var openAPI = new Wsdl2OpenApiConverter(documentedDefinitions(), "/").generate();

        assertTrue(openAPI.getInfo().getDescription().startsWith("Answers questions about cities."),
                "the service's documentation must lead info.description, ahead of the generated note");
    }

    @Test
    void definitionsDocumentationUsedWhereTheServiceDocumentsNothing() throws Exception {
        var openAPI = new Wsdl2OpenApiConverter(blzDefinitions, "/").generate();

        assertTrue(openAPI.getInfo().getDescription().startsWith("BLZService"));
    }

    @Test
    void configuredDescriptionWinsOverTheWsdlDocumentation() throws Exception {
        var openAPI = new Wsdl2OpenApiConverter(documentedDefinitions(), "/", Map.of(), null, "Configured.").generate();

        assertTrue(openAPI.getInfo().getDescription().startsWith("Configured."));
        assertFalse(openAPI.getInfo().getDescription().contains("Answers questions about cities."));
    }

    @Test
    void operationDocumentationBecomesOperationDescription() throws Exception {
        var operation = new Wsdl2OpenApiConverter(documentedDefinitions(), "/").generate()
                .getPaths().get("/get-city").getPost();

        assertEquals("Looks a city up by its name.", operation.getDescription());
        assertNull(operation.getSummary(), "a summary repeating the operation name is noise");
    }

    @Test
    void elementDocumentationReachesTheGeneratedYaml() throws Exception {
        assertTrue(generator(documentedDefinitions(), "/").contains("description: The name of the city to look up."));
    }

    @Test
    void documentationOfAFieldSurvivesNextToItsReference() throws Exception {
        var yaml = generator(documentedDefinitions(), "/");

        assertTrue(yaml.contains("description: Where the city is."),
                "an element documenting the named type it uses must keep its prose beside the $ref");
        assertTrue(yaml.contains("description: A point on the globe."),
                "the named type's own documentation belongs to its component");
    }

    private static Definitions documentedDefinitions() throws Exception {
        return Definitions.parse(new ResolverMap(), "classpath:/ws/documented.wsdl");
    }

    private static String generator(Definitions defs, String basePath) {
        return new Wsdl2OpenApiConverter(defs, basePath).generateYaml();
    }
}
