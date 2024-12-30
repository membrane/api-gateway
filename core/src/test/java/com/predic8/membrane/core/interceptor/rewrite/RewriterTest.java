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
package com.predic8.membrane.core.interceptor.rewrite;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exceptions.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.rewrite.RewriteInterceptor.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class RewriterTest {

    private RewriteInterceptor rewriter;

    @BeforeEach
    void setUp() throws Exception {
        rewriter = new RewriteInterceptor();
        List<Mapping> mappings = new ArrayList<>();
        mappings.add(new Mapping("/hello/(.*)", "/$1", null));
        rewriter.setMappings(mappings);

        rewriter.init(new HttpRouter());
    }

    @Test
    void rewriteWithoutTarget() throws Exception {
        Exchange exc = new Exchange(null) {{
            setRequest(new Request() {{
                setUri("/%");
            }});
            getDestinations().add("/%");
        }};
        rewriter.handleRequest(exc);

        assertEquals(400, exc.getResponse().getStatusCode());

        var pd = ProblemDetails.parse(exc.getResponse());
        assertEquals("https://membrane-api.io/error/user/path", pd.getType());

    }

    @Test
    void rewriteWithoutTarget2() throws Exception {
        Exchange exc = new Exchange(null);
        Request req = new Request();
        req.setUri("/%25");
        exc.setRequest(req);

        exc.getDestinations().add("/%25");

        rewriter.handleRequest(exc);

        assertEquals("/%25", exc.getRequest().getUri());
        assertEquals("/%25", exc.getDestinations().getFirst());
    }

    @Test
    void rewriteWithoutTarget3() throws Exception {
        Exchange exc = new Exchange(null);
        Request req = new Request();
        req.setUri("/hello/%25");
        exc.setRequest(req);

        exc.getDestinations().add("/hello/%25");

        rewriter.handleRequest(exc);

        assertEquals("/%25", exc.getRequest().getUri());
        assertEquals("/%25", exc.getDestinations().getFirst());
    }
}