/* Copyright 2023 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.http;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

public class ResponseBuilderTest {

    @Test
    void simpleOk() throws Exception {
        Response res = Response.ok("Hello").contentType("glue/instant").build();
        assertEquals(200,res.getStatusCode());
        assertEquals("Hello",res.getBodyAsStringDecoded());
        assertEquals("glue/instant",res.getHeader().getContentType());
    }
}
