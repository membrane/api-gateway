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

import static com.predic8.membrane.core.openapi.model.Request.get;
import static com.predic8.membrane.core.openapi.validators.JsonSchemaValidator.OBJECT;
import static org.junit.jupiter.api.Assertions.*;

class ExplodedObjectParameterTest extends AbstractValidatorTest {

    ParameterParser parameter;
    Parameter exploded;

    @Override
    protected String getOpenAPIFileName() {
        return "/openapi/specs/oas31/parameters/object.yaml";
    }

    /**
     * protected to be able to call super
     * @throws Exception
     */
    @BeforeEach
    protected void setUp() throws Exception {
        super.setUp();
        exploded = OpenAPIUtil.getParameter( OpenAPIUtil.getPath(validator.getApi(),"/exploded").getGet(),"rgb");
        parameter = AbstractParameterParser.instance(validator.getApi(),OBJECT, exploded);
    }

    @Nested
    class Exploded {

        @Test
        void colors() {
            ValidationErrors err = validator.validate(get().path("/exploded?R=100&G=200&B=150"));
            assertEquals(0, err.size());
        }

        @Test
        void valid() throws Exception {
            Map<String, List<String>> params = Map.of("R",List.of("100"),"G",List.of("200"),"B",List.of("150"));
            parameter.setValues(params);
            var fields = parameter.getJson();
            assertEquals(3, fields.size());
            assertEquals(100, fields.get("R").asInt());
            assertEquals(200, fields.get("G").asInt());
            assertEquals(150, fields.get("B").asInt());
        }
    }

}