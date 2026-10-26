/*
 *  Copyright 2022 predic8 GmbH, www.predic8.com
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.openapi.validators;

import com.predic8.membrane.core.openapi.*;
import com.predic8.membrane.core.openapi.model.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import java.util.*;

import static com.predic8.membrane.core.openapi.util.JsonTestUtil.*;
import static com.predic8.membrane.core.openapi.util.OpenAPITestUtils.*;
import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.*;
import static org.junit.jupiter.api.Assertions.*;


class EnumTest extends AbstractValidatorTest {

    private static final String OAS30 = "/openapi/specs/enum.yml";
    private static final String OAS31 = "/openapi/specs/oas31/enum.yml";

    @Override
    protected String getOpenAPIFileName() {
        return "/openapi/specs/enum.yml";
    }

    @Test
    void enumValid() {

        Map<String,String> m = new HashMap<>();
        m.put("state","amber");

        ValidationErrors errors = validator.validate(Request.post().path("/enum").body(mapToJson(m)));
        assertEquals(0,errors.size());
    }

    @Test
    void enumInvalid() {

        Map<String,String> m = new HashMap<>();
        m.put("state","blue");

        ValidationErrors errors = validator.validate(Request.post().path("/enum").body(mapToJson(m)));
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals(BODY, e.getContext().getValidatedEntityType());
        assertEquals("REQUEST", e.getContext().getValidatedEntity());
        assertTrue(e.getMessage().contains("enum"));
        assertEquals("REQUEST/BODY#/state", e.getContext().getLocationForRequest());
    }

    /**
     * The parser infers a type from the enum when the schema declares none (OAS 3.0 and 3.1), so
     * enum values of other types must not be rejected because of that type. See #3587.
     */
    @ParameterizedTest
    @ValueSource(strings = {OAS30, OAS31})
    void enumWithoutTypeAcceptsValuesOfAnyType(String spec) {
        assertEquals(List.of(), messages(spec, "/enum-without-type", "{\"state\": 42}"));
        assertEquals(List.of(), messages(spec, "/enum-without-type", "{\"state\": null}"));
        assertEquals(List.of(), messages(spec, "/enum-without-type", "{\"state\": \"green\"}"));
        assertEquals(List.of(), messages(spec, "/enum-object", "{\"a\": 1}"));
        assertEquals(List.of(), messages(spec, "/enum-mixed", "42"));
        assertEquals(List.of(), messages(spec, "/enum-mixed", "42.0"));
        assertEquals(List.of(), messages(spec, "/enum-mixed", "\"red\""));
        assertEquals(List.of(), messages(spec, "/enum-mixed", "[1, 2]"));
    }

    @ParameterizedTest
    @ValueSource(strings = {OAS30, OAS31})
    void enumWithoutTypeRejectsOtherValues(String spec) {
        assertEquals(List.of("43 is not part of the enum [42, red, [1,2]]."), messages(spec, "/enum-mixed", "43"));
        assertEquals(List.of("[2,1] is not part of the enum [42, red, [1,2]]."), messages(spec, "/enum-mixed", "[2, 1]"));
        assertEquals(List.of("{\"a\":2} is not part of the enum [{\"a\":1}]."), messages(spec, "/enum-object", "{\"a\": 2}"));
        assertEquals(1, messages(spec, "/enum-without-type", "{\"state\": 41}").size());
        assertEquals(1, messages(spec, "/enum-without-type", "{\"state\": \"blue\"}").size());
    }

    @ParameterizedTest
    @ValueSource(strings = {OAS30, OAS31})
    void enumOfNonStrings(String spec) {
        assertEquals(List.of(), messages(spec, "/enum-integer", "2"));
        assertEquals(List.of("3 is not part of the enum [1, 2]."), messages(spec, "/enum-integer", "3"));
        assertEquals(List.of(), messages(spec, "/enum-typed-object", "{\"a\": 1, \"b\": 2}"));
        assertEquals(List.of("{\"a\":1,\"b\":3} is not part of the enum [{\"a\":1,\"b\":2.0}, {\"b\":3}]."),
                messages(spec, "/enum-typed-object", "{\"a\": 1, \"b\": 3}"));
    }

    /**
     * An enum member is valid whatever type the parser inferred, but the other keywords of the
     * schema still apply to it.
     */
    @ParameterizedTest
    @ValueSource(strings = {OAS30, OAS31})
    void otherKeywordsStillApplyToEnumMembers(String spec) {
        assertEquals(List.of("Required property a is missing."), messages(spec, "/enum-typed-object", "{\"b\": 3}"));
    }

    /**
     * The type may be one the parser inferred, so a value that is no member is reported as such,
     * not as being of the wrong type.
     */
    @ParameterizedTest
    @ValueSource(strings = {OAS30, OAS31})
    void nonMemberOfOtherTypeIsReportedOnce(String spec) {
        assertEquals(List.of("true is not part of the enum [1, 2]."), messages(spec, "/enum-integer", "true"));
    }

    @ParameterizedTest
    @ValueSource(strings = {OAS30, OAS31})
    void enumOfQueryParameter(String spec) {
        assertEquals(List.of(), validator(spec).validate(Request.get().path("/enum-query?n=2")).getErrors().stream().map(ValidationError::getMessage).toList());
        assertEquals(1, validator(spec).validate(Request.get().path("/enum-query?n=3")).size());
    }

    private List<String> messages(String spec, String path, String body) {
        return validator(spec).validate(Request.post().path(path).json().body(body)).getErrors().stream()
                .map(ValidationError::getMessage)
                .toList();
    }

    private OpenAPIValidator validator(String spec) {
        return new OpenAPIValidator(new URIFactory(), new OpenAPIRecord(getApi(this, spec), new OpenAPISpec()));
    }
}
