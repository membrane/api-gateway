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