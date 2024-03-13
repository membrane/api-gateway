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
    void testValidateAdditionalQueryParametersValid() {
        var spec = new OpenAPI().components(new Components() {{
            addSecuritySchemes("test1", new SecurityScheme().name("foo").in(QUERY));
        }});

        assertNull(queryParameterValidator.validateAdditionalQueryParameters(
                new ValidationContext(),
                new HashMap<>(){{put("foo", "bar");}},
                spec
        ));
    }

    @Test
    void testValidateAdditionalQueryParametersInvalid() {
        var spec = new OpenAPI().components(new Components() {{
            addSecuritySchemes("test1", new SecurityScheme().name("foo").in(QUERY));
        }});

        assertNotNull(queryParameterValidator.validateAdditionalQueryParameters(
                new ValidationContext(),
                new HashMap<>(){{put("bar", "baz");}},
                spec
        ));
    }

    @Test
    void testCollectSchemeQueryParamKeys() {
        var spec = new OpenAPI().components(new Components() {{
            addSecuritySchemes("test1", new SecurityScheme().name("foo").in(QUERY));
            addSecuritySchemes("test2", new SecurityScheme().name("bar").in(QUERY));
        }});

        assertEquals(List.of("foo", "bar"), queryParameterValidator.collectSchemeQueryParamKeys(spec));
    }
}