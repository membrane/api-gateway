/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.openapi.validators.parameters;

import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.openapi.util.*;
import com.predic8.membrane.core.openapi.validators.*;
import io.swagger.v3.oas.models.parameters.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static com.predic8.membrane.core.openapi.validators.JsonSchemaValidator.ARRAY;
import static com.predic8.membrane.core.openapi.validators.JsonSchemaValidator.NUMBER;
import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.*;

class ArrayParameterTest extends AbstractValidatorTest {

    ParameterParser parameter;
    Parameter number;

    @Override
    protected String getOpenAPIFileName() {
        return "/openapi/specs/oas31/parameters/simple.yaml";
    }

    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        number = OpenAPIUtil.getParameter(OpenAPIUtil.getPath(validator.getApi(), "/array").getGet(), NUMBER);
        parameter = AbstractParameterParser.instance(validator.getApi(), ARRAY, number);
    }

    @Test
    void normal() throws Exception {
        Map<String, List<String>> params = Map.of("number",List.of("1","2","3"),"cuckoo",List.of("ignore"));
        parameter.setValues(params);
        var items = parameter.getJson();
        assertEquals(3, items.size());
        assertEquals(1, items.get(0).asInt());
        assertEquals(2, items.get(1).asInt());
        assertEquals(3, items.get(2).asInt());
    }

    @Test
    void single_null() throws Exception {
        parameter.setValues(Map.of("number",List.of("null")));
        JsonNode json = parameter.getJson();
        assertEquals(1, json.size());
        assertTrue(json.get(0).isNull());
    }

    @Test
    void single_null_values() throws Exception {
        parameter.setValues(Map.of("number", emptyList()));
        assertEquals(0, parameter.getJson().size());
    }
}