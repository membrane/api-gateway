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

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.rewrite.RewriteInterceptor.Mapping;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class RewriterTest {

    private RewriteInterceptor rewriter;

    @BeforeEach
    public void setUp() throws Exception {
        rewriter = new RewriteInterceptor();
        List<Mapping> mappings = new ArrayList<>();
        mappings.add( new Mapping("/hello/(.*)", "/$1", null));
        rewriter.setMappings(mappings);

        rewriter.init(new HttpRouter());
    }

    @Test
    void testRewriteWithoutTarget() throws Exception {
        assertThrows(URISyntaxException.class, () -> {
            Exchange exc = new Exchange(null);
            Request req = new Request();
            req.setUri("/%");
            exc.setRequest(req);

            exc.getDestinations().add("/%");

            rewriter.handleRequest(exc);

            System.out.println("uri: " + exc.getRequest().getUri());
            System.out.println("dest: " + exc.getDestinations().get(0));
        });
    }

    @Test
    void testRewriteWithoutTarget2() throws Exception {
        Exchange exc = new Exchange(null);
        Request req = new Request();
        req.setUri("/%25");
        exc.setRequest(req);

        exc.getDestinations().add("/%25");

        rewriter.handleRequest(exc);

        assertEquals("/%25", exc.getRequest().getUri());
        assertEquals("/%25", exc.getDestinations().get(0));
    }

    @Test
    void testRewriteWithoutTarget3() throws Exception {
        Exchange exc = new Exchange(null);
        Request req = new Request();
        req.setUri("/hello/%25");
        exc.setRequest(req);

        exc.getDestinations().add("/hello/%25");

        rewriter.handleRequest(exc);

        assertEquals("/%25", exc.getRequest().getUri());
        assertEquals("/%25", exc.getDestinations().get(0));
    }
}