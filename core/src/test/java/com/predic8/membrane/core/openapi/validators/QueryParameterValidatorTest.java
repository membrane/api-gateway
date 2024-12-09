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

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.parameters.*;
import io.swagger.v3.oas.models.security.*;
import org.junit.jupiter.api.*;

import java.net.*;
import java.util.*;

import static io.swagger.v3.oas.models.security.SecurityScheme.In.*;
import static io.swagger.v3.oas.models.security.SecurityScheme.Type.APIKEY;
import static org.junit.jupiter.api.Assertions.*;

class QueryParameterValidatorTest extends AbstractValidatorTest{

    QueryParameterValidator queryParameterValidator;

    @Override
   protected String getOpenAPIFileName() {
        return "/openapi/specs/query-params.yml";
    }

    @BeforeEach
    public void setUp() throws URISyntaxException {
        super.setUp();
        queryParameterValidator = new QueryParameterValidator(validator.getApi(),validator.getApi().getPaths().get("/cities"));
    }

    @Test
    void getPathAndOperationParameters() {

        List<Parameter> parameterSchemas = getParameterSchemas(queryParameterValidator);

        assertEquals(6,parameterSchemas.size());

        // All Parameters must have a name. Referenced params do not have a name.
        assertFalse(parameterSchemas.stream().anyMatch(param -> param.getName() == null));
    }

    private List<Parameter> getParameterSchemas(QueryParameterValidator val) {
        return val.getAllParameterSchemas(validator.getApi().getPaths().get("/cities").getGet());
    }

    /**
     * Test if the parser is inlining referenced params. foo and bar are defined on path level.
     * Works only with the newer parser and the option TODO
     */
    @Test
    void resolveReferencedParameter() {
        Operation get = validator.getApi().getPaths().get("/cities").getGet();
        assertTrue(operationHasParamWithName(get, "foo"));
        assertTrue(operationHasParamWithName(get, "bar"));
    }

    private static boolean operationHasParamWithName(Operation get, String name) {
        return get.getParameters().stream().anyMatch(param -> param.getName().equals(name));
    }

    @Test
    void testValidateAdditionalQueryParametersValid() {
        assertTrue(queryParameterValidator.validateAdditionalQueryParameters(
                new ValidationContext(),
                new HashMap<>(){{put("api-key", "234523");}},
                new OpenAPI().components(new Components() {{
                    addSecuritySchemes("schemaA", new SecurityScheme().type(APIKEY).name("api-key").in(QUERY));
                }})
        ).isEmpty());
    }

    @Test
    void testValidateAdditionalQueryParametersInvalid() {

        assertFalse(queryParameterValidator.validateAdditionalQueryParameters(
                new ValidationContext(),
                new HashMap<>(){{put("bar", "2315124");}},
                new OpenAPI().components(new Components() {{
                    addSecuritySchemes("schemaA", new SecurityScheme().type(APIKEY).name("api-key").in(QUERY));
                }})
        ).isEmpty());
    }

    @Test
    void testCollectSchemeQueryParamKeys() {
        var spec = new OpenAPI().components(new Components() {{
            addSecuritySchemes("schemaA", new SecurityScheme().type(APIKEY).name("api-key").in(QUERY));
            addSecuritySchemes("schemaB", new SecurityScheme().type(APIKEY).name("x-api-key").in(QUERY));
        }});

        assertEquals(List.of("api-key", "x-api-key"), queryParameterValidator.securitySchemeApiKeyQueryParamNames(spec));
    }
}