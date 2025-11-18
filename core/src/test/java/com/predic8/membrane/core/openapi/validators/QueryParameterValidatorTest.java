/* Copyright 2023 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.openapi.validators;

import com.fasterxml.jackson.databind.node.*;
import com.predic8.membrane.core.openapi.util.*;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.security.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static com.predic8.membrane.core.openapi.model.Request.*;
import static com.predic8.membrane.core.openapi.util.OpenAPITestUtils.*;
import static com.predic8.membrane.core.openapi.util.OpenAPIUtil.*;
import static com.predic8.membrane.core.openapi.validators.JsonSchemaValidator.STRING;
import static com.predic8.membrane.core.openapi.validators.QueryParameterValidator.*;
import static io.swagger.v3.oas.models.security.SecurityScheme.In.*;
import static io.swagger.v3.oas.models.security.SecurityScheme.Type.*;
import static org.junit.jupiter.api.Assertions.*;

class QueryParameterValidatorTest extends AbstractValidatorTest {

    QueryParameterValidator additionalValidator;
    PathItem additionalPathItem;

    QueryParameterValidator citiesValidator;
    PathItem citiesPathItem;

    QueryParameterValidator objectValidator;
    PathItem objectPathItem;

    ValidationContext ctx;


    @Override
    protected String getOpenAPIFileName() {
        return "/openapi/specs/query-params.yml";
    }

    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        citiesPathItem = validator.getApi().getPaths().get("/cities");
        citiesValidator = new QueryParameterValidator(validator.getApi(),citiesPathItem);

        additionalPathItem = validator.getApi().getPaths().get("/additional");
        additionalValidator = new QueryParameterValidator(validator.getApi(),additionalPathItem);

        objectPathItem = validator.getApi().getPaths().get("/object");
        objectValidator = new QueryParameterValidator(validator.getApi(),objectPathItem);

        ctx = new ValidationContext().statusCode(400).method("GET").path("/dummy");
    }

    @Test
    void emptyQueryParameter() {
        assertTrue(objectValidator.validate(ctx, get("/object?"), getGET("/object")).isEmpty());
    }

    @Test
    void getPathAndOperationParameters() {
        var parameters = citiesValidator.getAllParameter(getGET("/cities"));
        assertEquals(8, parameters.size());

        // All Parameters must have a name. Referenced params do not have a name.
        assertFalse(parameters.stream().anyMatch(param -> param.getName() == null));
    }

    /**
     * Test if the parser is inlining referenced params. foo and bar are defined on path level.
     * Works only with the newer parser and the option TODO
     */
    @Test
    void resolveReferencedParameter() {
        var get = getGET("/cities");
        assertTrue(operationHasParamWithName(get, "foo"));
        assertTrue(operationHasParamWithName(get, "bar"));
    }

    private Operation getGET(String path) {
        return validator.getApi().getPaths().get(path).getGet();
    }

    @Test
    void validateParameterAdditionalQueryParametersValid() {
        assertTrue(citiesValidator.validateAdditionalQueryParameters(
                ctx,
                Map.of("api-key", new TextNode("234523")),
                new OpenAPI().components(new Components() {{
                    addSecuritySchemes("schemaA", new SecurityScheme().type(APIKEY).name("api-key").in(QUERY));
                }})
        ).isEmpty());
    }

    @Test
    void validateParameterAdditionalQueryParametersInvalid() {
        assertFalse(citiesValidator.validateAdditionalQueryParameters(
                ctx,
                Map.of("bar", new TextNode("2315124")),
                new OpenAPI().components(new Components() {{
                    addSecuritySchemes("schemaA", new SecurityScheme().type(APIKEY).name("api-key").in(QUERY));
                }})
        ).isEmpty());
    }

    @Test
    void collectSchemeQueryParamKeys() {
        var spec = new OpenAPI().components(new Components() {{
            addSecuritySchemes("schemaA", new SecurityScheme().type(APIKEY).name("api-key").in(QUERY));
            addSecuritySchemes("schemaB", new SecurityScheme().type(APIKEY).name("x-api-key").in(QUERY));
        }});
        assertEquals(List.of("api-key", "x-api-key"), citiesValidator.securitySchemeApiKeyQueryParamNames(spec).stream().sorted().toList());
    }

    @Test
    void unknownQueryParameter() {
        var err = citiesValidator.validate(ctx,
                get().path("/cities?foo=1&limit=10&unknown=bad"), citiesPathItem.getGet());
        assertEquals(1, err.size());
        assertTrue(err.get(0).getMessage().contains("invalid"));
        assertTrue(err.get(0).getMessage().contains("unknown"));
    }


    @Nested
    class object {

        @Nested
        class explode {

            @Test
            void valid() {
                assertEquals(0, additionalValidator.validate(ctx,
                        get().path("/additional?a=1&b=txt&additional=t"), additionalPathItem.getGet()).size());
            }

            @Test
            void invalid() {
                var err = additionalValidator.validate(ctx,
                        get().path("/additional?a=wrong&b=txt&additional=t"), additionalPathItem.getGet());
                assertEquals(1, err.size());
                assertTrue(err.get(0).getMessage().contains("is not a number"));
                assertTrue(err.get(0).getMessage().contains("wrong"));
                assertEquals("/a", err.get(0).getContext().getJSONpointer());
            }
        }

        @Nested
        class explodeFalse {

            @Test
            void valid() {
                assertEquals(0, objectValidator.validate(ctx,
                        get().path("/object?pet=kind,mammal,age,9"), objectPathItem.getGet()).size());
            }

            @Test
            void invalid() {
                var err = objectValidator.validate(ctx,
                        get().path("/object?pet=kind,mammal,age,yes"), objectPathItem.getGet());
                assertEquals(1, err.size());
                assertTrue(err.get(0).getMessage().contains("is not a number"));
                assertEquals("/age", err.get(0).getContext().getJSONpointer());
                assertTrue(err.get(0).getMessage().contains("yes"));
            }
        }
    }

    @Nested
    class array {

        @Nested
        class explode {
            
            @Test
            void valid() {
                var err = citiesValidator.validate(ctx,
                        get().path("/cities?city=Bonn&city=New%20York&limit=10"), citiesPathItem.getGet());
                assertEquals(0, err.size());
            }

            @Test
            void invalid() {
                var err = citiesValidator.validate(ctx,
                        get().path("/cities?city=Bonn&city=Bielefeld&limit=10"), citiesPathItem.getGet());
                assertEquals(1, err.size());
                assertTrue(err.get(0).getMessage().contains("enum"));
                assertTrue(err.get(0).getMessage().contains("Bielefeld"));
            }
        }

        @Nested
        class explodeFalse {

            @Test
            void valid() {
                var err = citiesValidator.validate(ctx,
                        get().path("/cities?names=Joe,Jim&limit=10"), citiesPathItem.getGet());
                assertEquals(0, err.size());
            }

            @Test
            void invalid() {
                var err = citiesValidator.validate(ctx,
                        get().path("/cities?names=Joe,Jim,Jack&limit=10"), citiesPathItem.getGet());
                assertEquals(1, err.size());
                assertTrue(err.get(0).getMessage().contains("axLength"));
                assertTrue(err.get(0).getMessage().contains("Jack"));
                assertTrue(err.get(0).getMessage().contains("4"));
            }
        }
    }

    @Nested
    class additional {

        @Test
        void ignoreAdditionalFromObject() {
            assertEquals(0, additionalValidator.validate(ctx, get().path("/additional?a=1&b=txt&additional=t"), additionalPathItem.getGet()).size());
        }
    }

    @Nested
    class UtilMethods {

        @Test
        void get_QueryParameters() {
            var pathItem = getPathItem("/array");
            var qpv = new QueryParameterValidator(null, pathItem);
            var qp = qpv.getAllQueryParameters(pathItem.getGet());
            assertEquals(5, qp.size());
            assertTrue(qp.stream().allMatch(OpenAPIUtil::isQueryParameter));
        }


        @Test
        void get_QueryParameter_WithName() {
            var pathItem = getPathItem("/array");
            QueryParameterValidator qpv = new QueryParameterValidator(null, pathItem);
            assertEquals("String param", qpv.getQueryParameter(pathItem.getGet(), STRING).getDescription());
        }

        @Test
        void get_QueryParameter_Absent_ReturnsNull() {
            var pathItem = getPathItem("/array");
            var qpv = new QueryParameterValidator(null, pathItem);
            assertNull(qpv.getQueryParameter(pathItem.getGet(), "absent"));
        }

        @Test
        void get_Required_QueryParameters() {
            var pathItem = getPathItem("/required");
            var qpv = new QueryParameterValidator(null, pathItem);
            var rq = qpv.getRequiredQueryParameters(pathItem.getGet());
            assertEquals(2, rq.size());
        }

        @Test
        void get_PossibleObjectPropertiesNamesForOperation() {
            var propertyNames = citiesValidator.getPossibleObjectPropertiesNamesForOperation(         getPath(   citiesValidator.api, "/object").getGet());
            assertEquals(5, propertyNames.size());
            assertTrue(propertyNames.containsAll(Arrays.asList("age", "kind", "color","brand","power")));
        }

        @Test
        void get_QueryString() {
            assertEquals("bar=1", getQueryString(get().path("/foo?bar=1")));
        }

        /**
         * The encoding must be preserved. It must be applied after array or object parsing
         */
        @Test
        void preserveRAWencoding() {
            assertEquals("p=1%2C2", getQueryString(get().path("/foo?p=1%2C2")));
        }

        private PathItem getPathItem(String path) {
            return getPath(getApi(this, "/openapi/specs/oas31/parameters/simple.yaml"), path);
        }
    }

    private static boolean operationHasParamWithName(Operation get, String name) {
        return get.getParameters() != null && get.getParameters().stream().anyMatch(param -> param.getName().equals(name));
    }
}