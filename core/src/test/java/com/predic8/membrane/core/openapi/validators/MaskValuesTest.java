/*
 *  Copyright 2026 predic8 GmbH, www.predic8.com
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

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.predic8.membrane.core.openapi.*;
import com.predic8.membrane.core.openapi.model.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.util.*;
import org.junit.jupiter.api.*;

import java.math.*;
import java.util.*;

import static com.predic8.membrane.core.openapi.serviceproxy.APIProxy.*;
import static com.predic8.membrane.core.openapi.util.OpenAPITestUtils.*;
import static com.predic8.membrane.core.openapi.validators.ValidationError.MASK;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Masking is a render-time concern: the submitted value stays in the {@link ValidationError} and is
 * only masked when a message is rendered. Response and log can therefore be masked independently.
 */
public class MaskValuesTest {

    private static final ObjectMapper om = new ObjectMapper();

    private OpenAPIValidator validator(String spec) {
        OpenAPIValidator validator = new OpenAPIValidator(new URIFactory(),
                new OpenAPIRecord(parseOpenAPI(getClass().getResourceAsStream(spec)), new OpenAPISpec()));
        validator.getApi().setExtensions(new HashMap<>() {{
            put(X_MEMBRANE_VALIDATION, new HashMap<>() {{
                put(SECURITY, true);
                put(REQUESTS, true);
            }});
        }});
        return validator;
    }

    private static JsonNode object(String property, String value) {
        ObjectNode root = om.createObjectNode();
        root.put(property, value);
        return root;
    }

    private static JsonNode object(String property, BigDecimal value) {
        ObjectNode root = om.createObjectNode();
        root.put(property, value);
        return root;
    }

    @Test
    void numberMinimumRawKeepsValueMaskedHidesIt() {
        ValidationError e = validator("/openapi/specs/number.yml")
                .validate(Request.post().path("/number").body(new JsonBody(object("minimum", new BigDecimal(3))))).get(0);

        assertEquals("3 is smaller than the minimum of 5", e.getMessage());
        assertEquals("3 is smaller than the minimum of 5", e.getMessage(false));
        assertEquals(MASK + " is smaller than the minimum of 5", e.getMessage(true));
    }

    @Test
    void contentMapMasksOnlyWhenRequested() {
        ValidationError e = validator("/openapi/specs/number.yml")
                .validate(Request.post().path("/number").body(new JsonBody(object("minimum", new BigDecimal(3))))).get(0);

        assertEquals("3 is smaller than the minimum of 5", e.getContentMap(false).get("message"));
        assertEquals(MASK + " is smaller than the minimum of 5", e.getContentMap(true).get("message"));
    }

    @Test
    void stringValueIsNeverLeakedWhenMasked() {
        ValidationError e = validator("/openapi/specs/strings.yml")
                .validate(Request.post().path("/strings").body(new JsonBody(object("uuid", "B7AE38DD-7810-49E-B0BE-DF472F1343E0")))).get(0);

        assertTrue(e.getMessage(false).contains("B7AE38DD"), e.getMessage(false));
        String masked = e.getMessage(true);
        assertTrue(masked.contains("'" + MASK + "'"), masked);
        assertTrue(masked.contains("UUID"), masked);
        assertFalse(masked.contains("B7AE38DD"), masked);
    }

    @Test
    void sameErrorRendersBothWaysIndependently() {
        ValidationError e = validator("/openapi/specs/number.yml")
                .validate(Request.post().path("/number").body(new JsonBody(object("minimum", new BigDecimal(3))))).get(0);

        // The raw value stays in the error: response and log can be masked independently from one instance.
        assertEquals("3 is smaller than the minimum of 5", e.getMessage(false));
        assertEquals(MASK + " is smaller than the minimum of 5", e.getMessage(true));
        assertEquals("3 is smaller than the minimum of 5", e.getMessage(false));
    }
}
