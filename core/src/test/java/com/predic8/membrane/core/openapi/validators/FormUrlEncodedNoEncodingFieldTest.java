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

import com.predic8.membrane.core.openapi.model.Request;
import jakarta.mail.internet.ParseException;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_X_WWW_FORM_URLENCODED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Uses the OAS 3.1.1 "URL Encoded Form with JSON Values" example
 * (specs/multipart/no-encoding-field.oas.yml). The request body is declared as
 * {@code application/x-www-form-urlencoded} and the media type has no {@code encoding}
 * section.
 * <p>
 * Validation of {@code application/x-www-form-urlencoded} bodies is not implemented yet,
 * so these tests characterize the current behaviour: the spec is parsed and the operation
 * is matched, but the body is reported as not validated. Update them once form-urlencoded
 * validation is added.
 */
public class FormUrlEncodedNoEncodingFieldTest extends AbstractValidatorTest {

    @Override
    protected String getOpenAPIFileName() {
        return "/openapi/specs/x-www-form-urlencoded/no-encoding-field.oas.yml";
    }

    @Test
    void formUrlEncodedBodyIsNotYetValidated() throws ParseException {
        // address is a complex type stringified into the form value (RFC 1866).
        String body = "id=4b1f3d1e-1d9a-4a6e-8f3a-9b2c1d0e7a55"
                      + "&address=" + "%7B%22city%22%3A%22Berlin%22%2C%22country%22%3A%22DEU%22%7D";

        ValidationErrors errors = validator.validate(Request.put()
                .path("/v1/address")
                .mediaType(APPLICATION_X_WWW_FORM_URLENCODED)
                .body(body));

        assertEquals(1, errors.size(), () -> "Errors: " + errors);
        assertTrue(errors.get(0).getMessage().toLowerCase().contains("not implemented"),
                "Expected a 'not implemented' message but got: " + errors.get(0).getMessage());
    }
}
