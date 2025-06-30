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

package com.predic8.membrane.core.openapi.validators;

import com.predic8.membrane.core.openapi.model.Request;
import jakarta.mail.internet.ParseException;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PatternPropertiesTest extends AbstractValidatorTest{

    @Override
    protected String getOpenAPIFileName() {
        return "/openapi/specs/pattern-properties.yaml";
    }

    @Test
    void testPatternProperties() throws ParseException {
        ValidationErrors errors = validator.validate(Request.post().mediaType(APPLICATION_JSON).path("/test").body("""
                {"foo": []}
                """));
        assertEquals(1,errors.size());
        assertTrue(errors.toString().contains("Array has 0 items. This is less then minItems of 2."));
    }

    @Test
    void testValidRequest() throws ParseException {
        ValidationErrors errors = validator.validate(Request.post().mediaType(APPLICATION_JSON).path("/test").body("""
                {"foo": [1, 2]}
                """));
        assertEquals(0,errors.size());
    }

}
