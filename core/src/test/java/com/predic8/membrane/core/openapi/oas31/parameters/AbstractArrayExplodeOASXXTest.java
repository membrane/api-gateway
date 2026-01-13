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

package com.predic8.membrane.core.openapi.oas31.parameters;

import com.predic8.membrane.core.openapi.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.util.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.openapi.model.Request.*;
import static com.predic8.membrane.core.openapi.util.OpenAPITestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

public abstract class AbstractArrayExplodeOASXXTest {

    protected OpenAPIValidator validator;

    protected abstract String getOpenAPIFileName();

    @BeforeEach
    void setUp() {
        validator = new OpenAPIValidator(new URIFactory(), new OpenAPIRecord(
                parseOpenAPI(getResourceAsStream(this, getOpenAPIFileName())),
                new OpenAPISpec()
        ));
    }

    @Test
    void numbers() {
        assertEquals(0, validator.validate(get().path("/foo?numbers=1&numbers=2")).size());
    }

    @Test
    void strings() {
        assertEquals(0, validator.validate(get().path("/foo?strings=abc&strings=def")).size());
    }

    @Test
    void numberAsString() {
        assertEquals(0, validator.validate(get().path("/foo?strings=abc&strings=123")).size());
    }

    @Test
    void onlyNumberAsString() {
        assertEquals(0, validator.validate(get().path("/foo?strings=456&strings=123")).size());
    }

    @Test
    void oneNumberAsString() {
        assertEquals(0, validator.validate(get().path("/foo?strings=456")).size());
    }

    @Nested
    class StringRestrictions {

        @Test
        void maxLengthExceeded() {
            var err = validator.validate(get().path("/foo?strings=abcdefghijk")); // 11 chars
            assertEquals(1, err.size());
            assertTrue(err.get(0).getMessage().contains("MaxLength of 10 is exceeded"));
        }

        @Test
        void minLengthViolated() {
            var err = validator.validate(get().path("/foo?strings=a")); // 1 char
            assertEquals(1, err.size());
            assertTrue(err.get(0).getMessage().contains("shorter than the minLength of 2"));
        }

        @Test
        void patternValidLettersAndDigits() {
            assertEquals(0, validator.validate(get().path("/foo?strings=abc123")).size());
        }

        @Test
        void patternViolatedUnderscore() {
            var err = validator.validate(get().path("/foo?strings=ab_c"));
            assertEquals(1, err.size());
            assertTrue(err.get(0).getMessage().contains("does not match the pattern"));
        }

        @Test
        void enumValidNumericString() {
            assertEquals(0, validator.validate(get().path("/foo?strings=123&strings=456")).size());
        }

        @Test
        void enumValidAlpha() {
            assertEquals(0, validator.validate(get().path("/foo?strings=abc&strings=def")).size());
        }

        @Test
        void enumViolated() {
            var err = validator.validate(get().path("/foo?strings=zzz"));
            assertEquals(1, err.size());
            assertTrue(err.get(0).getMessage().contains("'zzz' is not part of the enum"));
        }

        @Test
        void multipleViolationsReportedPerItem() {
            // violates enum and pattern (contains '-') and length is ok
            var err = validator.validate(get().path("/foo?strings=ab-g"));
            assertFalse(err.isEmpty());
            assertTrue(err.stream().anyMatch(e -> e.getMessage().contains("pattern")));
            assertTrue(err.stream().anyMatch(e -> e.getMessage().contains("enum")));
        }
    }

}
