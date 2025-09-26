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

package com.predic8.membrane.core.openapi.model;

import com.predic8.membrane.core.security.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.openapi.util.JsonTestUtil.*;
import static com.predic8.membrane.core.security.HttpSecurityScheme.*;
import static org.junit.jupiter.api.Assertions.*;

class RequestTest {

    @Test
    void doesSettingAJsonBodySetTheMimeType() {
        assertEquals(APPLICATION_JSON, Request.post().path("/boolean").body(new JsonBody(mapToJson(new HashMap<>()))).getMediaType().getBaseType());
    }

    @Test
    void hasSchemeBasic() {
        assertTrue(Request.get().securitySchemes(List.of(BasicHttpSecurityScheme.BASIC())).hasScheme(BASIC()));
    }

    @Test
    void hasSchemeBearer() {
        assertTrue(Request.get().securitySchemes(List.of(BearerHttpSecurityScheme.BEARER())).hasScheme(BEARER()));
    }

    @Test
    void hasSchemeWrong() {
        assertFalse(Request.get().securitySchemes(List.of(BearerHttpSecurityScheme.BEARER())).hasScheme(BASIC()));
    }
}