package com.predic8.membrane.core.kubernetes;

import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.balancer.LoadBalancingInterceptor;
import com.predic8.membrane.core.interceptor.beautifier.BeautifierInterceptor;
import com.predic8.membrane.core.interceptor.flow.ResponseInterceptor;
import com.predic8.membrane.core.interceptor.lang.SetCookiesInterceptor;
import com.predic8.membrane.core.interceptor.log.LogInterceptor;
import com.predic8.membrane.core.interceptor.ratelimit.RateLimitInterceptor;
import com.predic8.membrane.core.interceptor.rewrite.RewriteInterceptor;
import com.predic8.membrane.core.interceptor.xml.Xml2JsonInterceptor;
import com.predic8.membrane.core.openapi.serviceproxy.APIProxy;
import com.predic8.membrane.core.proxies.AbstractServiceProxy;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.events.*;

import java.io.StringReader;
import java.util.*;

import static com.predic8.membrane.core.openapi.serviceproxy.OpenAPISpec.YesNoOpenAPIOption.YES;
import static org.junit.jupiter.api.Assertions.*;

class GenericYamlParserMembraneTest {

    @Test
    void parseSimpleApi() {
        String yaml = """
          port: 2000
          target:
            url: https://api.predic8.de
        """;

        APIProxy api = GenericYamlParser.parse("api", APIProxy.class, events(yaml),null);

        assertEquals(2000, api.getPort());
        assertEquals("https://api.predic8.de", api.getTarget().getUrl());
    }

    @Test
    void parsePathAndInterceptors() {
        String yaml = """
          port: 2000
          path:
            value: /names
          interceptors:
            - rateLimiter:
                requestLimit: 3
                requestLimitDuration: PT30S
            - rewriter:
                mappings:
                  - map:
                      from: ^/names/(.*)
                      to: /restnames/name\\.groovy\\?name=$1
            - response:
                interceptors:
                  - beautifier: {}
                  - xml2Json: {}
                  - log: {}
          target:
            url: https://api.predic8.de
        """;

        APIProxy api = GenericYamlParser.parse("api", APIProxy.class, events(yaml),null);

        assertEquals("/names", api.getPath().getValue());
        assertEquals(2000, api.getPort());
        assertEquals("https://api.predic8.de", api.getTarget().getUrl());

        List<Interceptor> interceptors = api.getInterceptors();
        assertEquals(3, interceptors.size());
        assertInstanceOf(RateLimitInterceptor.class, interceptors.get(0));
        assertInstanceOf(RewriteInterceptor.class, interceptors.get(1));
        assertInstanceOf(ResponseInterceptor.class, interceptors.get(2));

        RateLimitInterceptor rateLimitInterceptor = (RateLimitInterceptor) interceptors.get(0);
        assertEquals(3, rateLimitInterceptor.getRequestLimit());

        RewriteInterceptor rewriteInterceptor = (RewriteInterceptor) interceptors.get(1);
        assertEquals(1, rewriteInterceptor.getMappings().size());
        RewriteInterceptor.Mapping m = rewriteInterceptor.getMappings().getFirst();
        assertEquals("^/names/(.*)", m.getFrom());
        assertEquals("/restnames/name\\.groovy\\?name=$1", m.getTo());

        ResponseInterceptor responseInterceptor = (ResponseInterceptor) interceptors.get(2);
        List<Interceptor> responseInterceptorInterceptors = responseInterceptor.getInterceptors();
        assertEquals(3, responseInterceptorInterceptors.size());
        assertInstanceOf(BeautifierInterceptor.class, responseInterceptorInterceptors.get(0));
        assertInstanceOf(Xml2JsonInterceptor.class, responseInterceptorInterceptors.get(1));
        assertInstanceOf(LogInterceptor.class, responseInterceptorInterceptors.get(2));
    }

    @Test
    void parseOpenApi() {
        String yaml = """
          specs:
            - openapi:
                location: fruitshop-api.yml
                validateRequests: "yes"
        """;

        APIProxy api = GenericYamlParser.parse("api", APIProxy.class, events(yaml),null);

        assertNotNull(api.getSpecs());
        assertEquals(1, api.getSpecs().size());
        assertEquals("fruitshop-api.yml", api.getSpecs().getFirst().getLocation());
        assertEquals(YES, api.getSpecs().getFirst().getValidateRequests());
    }

    @Test
    void parseTypes() {
        String yaml = """
          port: 8080
          interceptors:
            - balancer:
                sessionTimeout: 10000
            - setCookies:
                cookies:
                  - cookie:
                      name: foo
                      value: bar
                      secure: false
        """;

        APIProxy api = GenericYamlParser.parse("api", APIProxy.class, events(yaml),null);

        LoadBalancingInterceptor lb = (LoadBalancingInterceptor) api.getInterceptors().getFirst();
        assertInstanceOf(Long.class, lb.getSessionTimeout());
        assertEquals(10000, lb.getSessionTimeout());

        SetCookiesInterceptor sc = (SetCookiesInterceptor) api.getInterceptors().get(1);
        assertEquals("foo", sc.getCookies().getFirst().getName());
        assertEquals("bar", sc.getCookies().getFirst().getValue());
        assertInstanceOf(Boolean.class, sc.getCookies().getFirst().isSecure());
        assertFalse(sc.getCookies().getFirst().isSecure());
    }

    @Test
    void parseTopLevelRef() {
        AbstractServiceProxy.Target target = new AbstractServiceProxy.Target();
        target.setUrl("https://ref.example");

        TestRegistry reg = new TestRegistry().with("target", target);

        String yaml = """
          $ref: target
        """;

        APIProxy api = GenericYamlParser.parse("api", APIProxy.class, events(yaml), reg);

        assertSame(target, api.getTarget());
        assertEquals("https://ref.example", api.getTarget().getUrl());
    }

    @Test
    void parseListItemRef() {
        ResponseInterceptor responseInterceptor = new ResponseInterceptor();
        TestRegistry reg = new TestRegistry().with("response", responseInterceptor);

        String yaml = """
          interceptors:
            - $ref: response
        """;

        APIProxy api = GenericYamlParser.parse("api", APIProxy.class, events(yaml), reg);

        assertEquals(1, api.getInterceptors().size());
        assertSame(responseInterceptor, api.getInterceptors().getFirst());
    }

    private static Iterator<Event> events(String yaml) {
        Iterable<Event> iterable = new Yaml().parse(new StringReader(yaml));
        List<Event> filtered = new ArrayList<>();
        for (Event e : iterable) {
            if (e instanceof StreamStartEvent || e instanceof DocumentStartEvent) continue;
            if (e instanceof StreamEndEvent || e instanceof DocumentEndEvent) break;
            filtered.add(e);
        }
        return filtered.iterator();
    }

    static class TestRegistry implements BeanRegistry {
        private final Map<String, Object> refs = new HashMap<>();
        TestRegistry with(String key, Object v) { refs.put(key, v); return this; }
        @Override public Object resolveReference(String ref) { return refs.get(ref); }
    }
}
