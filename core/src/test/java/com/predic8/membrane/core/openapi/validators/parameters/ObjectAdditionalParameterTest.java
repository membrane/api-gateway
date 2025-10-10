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

import com.predic8.membrane.core.openapi.util.*;
import com.predic8.membrane.core.openapi.validators.*;
import io.swagger.v3.oas.models.parameters.*;
import org.jetbrains.annotations.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ObjectAdditionalParameterTest extends AbstractValidatorTest {

    @Override
    protected String getOpenAPIFileName() {
        return "/openapi/specs/oas31/parameters/object-additional.yaml";
    }

    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
    }

    @Nested
    class ExplodeFalse {

        @Test
        void noAdditionalProperties() throws Exception {
            Parameter parameter = OpenAPIUtil.getParameter( OpenAPIUtil.getPath(validator.getApi(),"/additional-schema-explode-false").getGet(),"foo");
            ParameterParser parameterParser = getParser(parameter);
            parameterParser.setValues(Map.of("foo",List.of("a,baz")));
            var fields = parameterParser.getJson();
            assertEquals(1, fields.size());
            assertEquals("baz", fields.get("a").asText());
        }

        @Test
        void additionalSchemaRightType() throws Exception {
            Parameter parameter = OpenAPIUtil.getParameter( OpenAPIUtil.getPath(validator.getApi(),"/additional-schema-explode-false").getGet(),"foo");
            ParameterParser parameterParser = getParser(parameter);
            parameterParser.setValues(Map.of("foo",List.of("a,baz,b,314")));
            var fields = parameterParser.getJson();
            assertEquals(2, fields.size());
            assertEquals("baz", fields.get("a").asText());
            assertEquals(314, fields.get("b").asInt());
        }

        @Test
        void additionalBooleanTrue() throws Exception {
            Parameter parameter = OpenAPIUtil.getParameter( OpenAPIUtil.getPath(validator.getApi(),"/additional-boolean-true-explode-false").getGet(),"foo");
            ParameterParser parameterParser = getParser(parameter);
            parameterParser.setValues(Map.of("foo",List.of("a,baz,b,314")));
            var fields = parameterParser.getJson();
            assertEquals(2, fields.size());
            assertEquals("baz", fields.get("a").asText());
            assertEquals(314, fields.get("b").asInt());
        }

        @Test
        void additionalBooleanFalse() throws Exception {
            Parameter parameter = OpenAPIUtil.getParameter( OpenAPIUtil.getPath(validator.getApi(),"/additional-boolean-false-explode-false").getGet(),"foo");
            ParameterParser parameterParser = getParser(parameter);
            parameterParser.setValues(Map.of("foo",List.of("a,baz,b,314")));
            var fields = parameterParser.getJson();
            System.out.println("fields = " + fields);
            assertEquals(1, fields.size());
            assertEquals("baz", fields.get("a").asText());
            assertNull(fields.get("b"));
        }
    }

    private @NotNull ParameterParser getParser(Parameter parameter) {
        return AbstractParameterParser.instance(validator.getApi(), "object", parameter);
    }
}