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

    /**
     * patternProperties regexes are not anchored, so {@code ^x-} must match {@code x-trace}.
     */
    @Nested
    class PatternProperties {

        @Test
        void unanchoredPatternCollectsParameter() throws Exception {
            final var filter = OpenAPIUtil.getParameter(OpenAPIUtil.getPath(validator.getApi(), "/pattern").getGet(), "filter");
            final var parser = AbstractParameterParser.instance(validator.getApi(), OBJECT, filter);
            parser.setValues(Map.of("x-trace", List.of("123")));
            final var fields = parser.getJson();
            assertEquals(1, fields.size());
            assertEquals(123, fields.get("x-trace").asInt());
        }

        @Test
        void validValue() {
            final var err = validator.validate(get().path("/pattern?x-trace=123"));
            assertEquals(0, err.size(), err.toString());
        }

        @Test
        void invalidValue() {
            final var err = validator.validate(get().path("/pattern?x-trace=abc"));
            assertEquals(1, err.size(), err.toString());
            assertFalse(err.getFirst().getMessage().contains("Unknown query parameter"), err.toString());
        }

        @Test
        void nonMatchingNameIsUnknown() {
            final var err = validator.validate(get().path("/pattern?y-trace=1"));
            assertEquals(1, err.size(), err.toString());
            assertTrue(err.getFirst().getMessage().contains("Unknown query parameter 'y-trace'"), err.toString());
        }

        /**
         * The unanchored pattern "a" also matches the declared string parameter "name", which must not be pulled
         * into the object and validated as integer.
         */
        @Test
        void declaredParameterIsNotClaimedByPattern() {
            final var err = validator.validate(get().path("/pattern-collision?name=abc&xa=1"));
            assertEquals(0, err.size(), err.toString());
        }
    }

}