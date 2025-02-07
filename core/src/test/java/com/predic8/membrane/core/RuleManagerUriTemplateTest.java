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
package com.predic8.membrane.core;

import com.predic8.membrane.*;
import com.predic8.membrane.core.config.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.proxies.Proxy;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import java.net.*;
import java.util.stream.*;

import static com.predic8.membrane.core.http.Request.*;
import static org.junit.jupiter.api.Assertions.*;

public class RuleManagerUriTemplateTest extends AbstractTestWithRouter {

    static Stream<Arguments> matches() {
        return Stream.of(Arguments.of("/foo","/foo"),
                Arguments.of("/foo/{fid}/{gid}/{hid}","/foo/7/8/9"),
                Arguments.of("{fid}","7"),
                Arguments.of("/foo/{fid}","/foo/7"),
                Arguments.of("{a}-{b}","1-2")
                );
    }

    @ParameterizedTest
    @MethodSource("matches")
    void pathMatchesUriTemplate(String uriTemplate,String path) throws Exception {
        APIProxy p = new APIProxy();
        assertEquals(p, getMatchingProxy(uriTemplate, path, p));
    }

    static Stream<Arguments> missfits() {
        return Stream.of(Arguments.of("/foo","/bar"),
                Arguments.of("{fid}/{fid}","7")
        );
    }

    @ParameterizedTest
    @MethodSource("missfits")
    void pathDoesntMatchesUriTemplate(String uriTemplate,String path) throws Exception {
        APIProxy p = new APIProxy();
        assertNotEquals(p, getMatchingProxy(uriTemplate, path, p));
    }

    private static Proxy getMatchingProxy(String uriTemplate, String path, APIProxy p) throws URISyntaxException {
        p.setPath(new Path(false, uriTemplate));
        p.init(router);
        return new RuleManager() {{
            proxies.add(p);
        }}.getMatchingRule(get(path).buildExchange());
    }
}