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
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ObjectParameterParserTest extends AbstractValidatorTest {

    ParameterParser parameter;
    Parameter color;

    @Override
    protected String getOpenAPIFileName() {
        return "/openapi/specs/oas31/parameters/object.yaml";
    }

    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        color = OpenAPIUtil.getParameter( OpenAPIUtil.getPath(validator.getApi(),"/color").getGet(),"rgb");
        parameter = AbstractParameterParser.instance( validator.getApi(),"object", color);
    }

    @Nested
    class ExplodeFalse {


        @Test
        void valid() throws Exception {
            Map<String, List<String>> params = Map.of("rgb",List.of("R,100,G,200,B,150"));
            parameter.setValues(params);
            var fields = parameter.getJson();
            assertEquals(3, fields.size());
            assertEquals(100, fields.get("R").asInt());
            assertEquals(200, fields.get("G").asInt());
            assertEquals(150, fields.get("B").asInt());
        }

        @Test
        void one_element_more_additionalProperties_true() throws Exception {
            parameter.setValues(Map.of("rgb",List.of("R,100,cuckoo,314,G,200,B,150,other,baz")));
            var fields = parameter.getJson();
            assertEquals(5, fields.size());
            assertEquals(100, fields.get("R").asInt());
            assertEquals(200, fields.get("G").asInt());
            assertEquals(150, fields.get("B").asInt());
            assertEquals("baz", fields.get("other").asText());
            assertEquals(314, fields.get("cuckoo").asInt());
        }

        @Test
        void one_too_much_no_value() throws Exception {
            parameter.setValues(Map.of("rgb",List.of("R,100,G,200,B,150,NoValue")));
            var fields = parameter.getJson();
            assertEquals(4, fields.size());
            assertEquals(100, fields.get("R").asInt());
            assertEquals(200, fields.get("G").asInt());
            assertEquals(150, fields.get("B").asInt());
            assertNotNull(fields.get("NoValue"));
        }

        @Test
        void empty() throws Exception {
            parameter.setValues(Map.of("rgb",List.of("")));
            assertEquals(0, parameter.getJson().size());
        }
    }
}