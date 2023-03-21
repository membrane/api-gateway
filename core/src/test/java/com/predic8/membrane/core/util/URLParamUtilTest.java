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

package com.predic8.membrane.core.util;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import org.junit.jupiter.api.*;

import java.io.*;
import java.net.*;
import java.util.*;

import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.util.URLParamUtil.*;
import static com.predic8.membrane.core.util.URLParamUtil.DuplicateKeyOrInvalidFormStrategy.*;
import static org.junit.jupiter.api.Assertions.*;

public class URLParamUtilTest {

    Exchange e1;

    @BeforeEach
    void setUp() throws URISyntaxException {
        e1 = Request.get("foo?a=1&b=2&c=abc").buildExchange();
    }

    @Test
    void hasNoFormParamsNoEncoding() throws URISyntaxException, IOException {
        Exchange exc = new Request.Builder().post("/dummy").body("foo=7&bar&baz").header(CONTENT_TYPE,APPLICATION_X_WWW_FORM_URLENCODED).buildExchange();
        assertFalse(hasNoFormParams(exc));
    }

    @Test
    void hasNoFormParamsWithEncoding() throws URISyntaxException, IOException {
        Exchange exc = new Request.Builder().post("/dummy").body("foo=7&bar&baz").header(CONTENT_TYPE,APPLICATION_X_WWW_FORM_URLENCODED + "; charset=ISO-8859-1").buildExchange();
        assertFalse(hasNoFormParams(exc));
    }

    @Test
    void getParams() throws Exception {
        Map<String, String> params = URLParamUtil.getParams(new URIFactory(), e1, ERROR);
        assertEquals(3,params.size());
        assertEquals("1",params.get("a"));
        assertEquals("2",params.get("b"));
        assertEquals("abc",params.get("c"));
    }
}