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

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.parameters.*;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.*;

import java.util.*;

import static io.swagger.v3.oas.models.security.SecurityScheme.In.HEADER;
import static io.swagger.v3.oas.models.security.SecurityScheme.In.QUERY;
import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.*;

class QueryParameterValidatorTest extends AbstractValidatorTest{

    QueryParameterValidator queryParameterValidator;

    @Override
   protected String getOpenAPIFileName() {
        return "/openapi/specs/query-params.yml";
    }

    @BeforeEach
    public void setUp() {
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

    @Test
    void resolveReferencedParameter() {
        Parameter referencingParam = validator.getApi().getPaths().get("/cities").getParameters().get(1);
        Parameter resolvedParam = queryParameterValidator.resolveReferencedParameter(referencingParam);
        assertEquals("bar",resolvedParam.getName());
    }

    @Test
    void testCheckSecurityRequirementsSimple() {
        List<SecurityRequirement> requirements = List.of(new SecurityRequirement() {{
            put("test1", emptyList());
        }});
        var spec = new OpenAPI().components(new Components() {{
            addSecuritySchemes("test1", new SecurityScheme().name("apiKey").in(QUERY));
        }});

        Pair<Map<String, String>, Boolean> res = checkParam(requirements, spec, "apiKey");
        assertTrue(res.getRight());
        assertTrue(res.getLeft().isEmpty());
        assertFalse(checkParam(requirements, spec, "wrongParam").getRight());
    }

    @Test
    void testCheckSecurityRequirementsNonQueryScheme() {
        List<SecurityRequirement> requirements = List.of(new SecurityRequirement() {{
            put("test1", emptyList());
        }});
        var spec = new OpenAPI().components(new Components() {{
            addSecuritySchemes("test1", new SecurityScheme().name("apiKey").in(HEADER));
        }});

        Pair<Map<String, String>, Boolean> res = checkParam(requirements, spec, "apiKey");
        assertTrue(res.getRight());
        assertEquals(1, res.getLeft().size());
    }

    @Test
    void testCheckSecurityRequirementsOr() {
        List<SecurityRequirement> requirements = List.of(new SecurityRequirement() {{
            put("foo", emptyList());
        }}, new SecurityRequirement() {{
            put("bar", emptyList());
        }});
        var spec = new OpenAPI().components(new Components() {{
            addSecuritySchemes("foo", new SecurityScheme().name("apiKey").in(QUERY));
            addSecuritySchemes("bar", new SecurityScheme().name("apiToken").in(QUERY));
        }});

        assertTrue(checkParam(requirements, spec, "apiKey").getRight());
        assertTrue(checkParam(requirements, spec, "apiToken").getRight());
    }

    @Test
    void testCheckSecurityRequirementsAnd() {
        List<SecurityRequirement> requirements = List.of(new SecurityRequirement() {{
            put("foo", emptyList());
            put("bar", emptyList());
        }});
        var spec = new OpenAPI().components(new Components() {{
            addSecuritySchemes("foo", new SecurityScheme().name("apiKey").in(QUERY));
            addSecuritySchemes("bar", new SecurityScheme().name("apiToken").in(QUERY));
        }});

        assertTrue(checkParam2(requirements, spec, "apiKey", "apiToken"));
        assertFalse(checkParam(requirements, spec, "apiToken").getRight());
    }

    @Test
    void testValidateParamsPresent() {
        assertTrue(queryParameterValidator.validateParams(
                new SecurityScheme().name("apiKey").in(QUERY),
                Map.of("apiKey", "12345")
        ));
    }

    @Test
    void testValidateParamsAbsent() {
        assertFalse(queryParameterValidator.validateParams(
                new SecurityScheme().name("apiKey").in(QUERY),
                Map.of()
        ));
    }

    @Test
    void testValidateParamsInvalid() {
        assertFalse(queryParameterValidator.validateParams(
                new SecurityScheme().name("apiToken").in(QUERY),
                Map.of("apiKey", "12345")
        ));
        assertFalse(queryParameterValidator.validateParams(
                new SecurityScheme().name("apiKey").in(QUERY),
                Map.of("apiToken", "12345")
        ));
    }

    private Pair<Map<String, String>, Boolean> checkParam(List<SecurityRequirement> requirements, OpenAPI spec, String key) {
        Map<String, String> map = new HashMap<>() {{
            put(key, "12345");
        }};
        return Pair.of(map, queryParameterValidator.checkSecurityRequirements(requirements, map, spec));
    }

    private boolean checkParam2(List<SecurityRequirement> requirements, OpenAPI spec, String key, String key2) {
        return queryParameterValidator.checkSecurityRequirements(requirements, new HashMap<>() {{
            put(key, "12345");
            put(key2, "12345");
        }}, spec);
    }
}