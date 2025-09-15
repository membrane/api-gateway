package com.predic8.membrane.core.config.spring.k8s;

import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.administration.AdminConsoleInterceptor;
import com.predic8.membrane.core.interceptor.flow.RequestInterceptor;
import com.predic8.membrane.core.interceptor.groovy.GroovyInterceptor;
import com.predic8.membrane.core.interceptor.ratelimit.RateLimitInterceptor;
import com.predic8.membrane.core.interceptor.rewrite.RewriteInterceptor;
import com.predic8.membrane.core.interceptor.templating.TemplateInterceptor;
import com.predic8.membrane.core.interceptor.flow.ReturnInterceptor;
import com.predic8.membrane.core.kubernetes.BeanRegistry;
import com.predic8.membrane.core.openapi.serviceproxy.APIProxy;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.events.*;

import java.io.StringReader;
import java.util.*;

import static com.predic8.membrane.core.openapi.serviceproxy.OpenAPISpec.YesNoOpenAPIOption.YES;
import static org.junit.jupiter.api.Assertions.*;

class EnvelopeTest {

    @Test
    void routerConfConfig() {
        String yaml = """
        apiVersion: membrane-soa.org/v1beta1
        kind: api
        metadata:
          name: Fruitshop
        spec:
          port: 2000
          specs:
            - openapi:
                location: fruitshop-api.yml
                validateRequests: "yes"
        ---
        apiVersion: membrane-soa.org/v1beta1
        kind: api
        metadata:
          name: api-rewrite
        spec:
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
        ---
        apiVersion: membrane-soa.org/v1beta1
        kind: api
        metadata:
          name: header
        spec:
          port: 2000
          path:
            value: /header
          interceptors:
            - request:
                interceptors:
                  - groovy:
                      src: |
                        println "Request headers:"
                        CONTINUE
                  - template:
                      contentType: application/json
                      textTemplate: '{ "ok": 1 }'
                  - return:
                      statusCode: 200
        ---
        apiVersion: membrane-soa.org/v1beta1
        kind: api
        metadata:
          name: api
        spec:
          port: 2000
          target:
            url: https://api.predic8.de
        ---
        apiVersion: membrane-soa.org/v1beta1
        kind: api
        metadata:
          name: admin
        spec:
          port: 9000
          interceptors:
            - adminConsole: {}
        """;

        List<Envelope> docs = parseEnvelopes(yaml,null);

        assertEquals(5, docs.size());

        // Fruitshop
        Envelope e0 = docs.getFirst();
        assertEquals("api", e0.kind);
        assertEquals("membrane-soa.org/v1beta1", e0.apiVersion);
        assertEquals("Fruitshop", e0.metadata.name);
        assertInstanceOf(APIProxy.class, e0.getSpec());
        APIProxy a0 = (APIProxy) e0.getSpec();
        assertEquals(2000, a0.getPort());
        assertEquals(1, a0.getSpecs().size());
        assertEquals("fruitshop-api.yml", a0.getSpecs().getFirst().getLocation());
        assertEquals(YES, a0.getSpecs().getFirst().getValidateRequests());

        // api-rewrite
        Envelope e1 = docs.get(1);
        APIProxy a1 = (APIProxy) e1.getSpec();
        assertEquals("api-rewrite", e1.metadata.name);
        assertEquals(2000, a1.getPort());
        assertEquals("/names", a1.getPath().getValue());
        List<Interceptor> is1 = a1.getInterceptors();
        assertEquals(3, is1.size());
        assertInstanceOf(RateLimitInterceptor.class, is1.get(0));
        assertInstanceOf(RewriteInterceptor.class, is1.get(1));
        RewriteInterceptor rw = (RewriteInterceptor) is1.get(1);
        assertEquals(1, rw.getMappings().size());
        assertEquals("^/names/(.*)", rw.getMappings().getFirst().getFrom());
        assertEquals("/restnames/name\\.groovy\\?name=$1", rw.getMappings().getFirst().getTo());
        assertEquals("https://api.predic8.de", a1.getTarget().getUrl());

        // header
        Envelope e2 = docs.get(2);
        APIProxy a2 = (APIProxy) e2.getSpec();
        assertEquals("header", e2.metadata.name);
        assertEquals("/header", a2.getPath().getValue());
        assertEquals(1, a2.getInterceptors().size());
        assertInstanceOf(RequestInterceptor.class, a2.getInterceptors().getFirst());
        List<Interceptor> reqChain = ((RequestInterceptor) a2.getInterceptors().getFirst()).getInterceptors();
        assertEquals(3, reqChain.size());
        assertInstanceOf(GroovyInterceptor.class, reqChain.get(0));
        assertInstanceOf(TemplateInterceptor.class, reqChain.get(1));
        assertInstanceOf(ReturnInterceptor.class, reqChain.get(2));

        // api minimal
        Envelope e3 = docs.get(3);
        APIProxy a3 = (APIProxy) e3.getSpec();
        assertEquals("api", e3.metadata.name);
        assertEquals(2000, a3.getPort());
        assertEquals("https://api.predic8.de", a3.getTarget().getUrl());

        // admin
        Envelope e4 = docs.get(4);
        APIProxy a4 = (APIProxy) e4.getSpec();
        assertEquals("admin", e4.metadata.name);
        assertEquals(9000, a4.getPort());
        assertTrue(a4.getInterceptors().stream().anyMatch(i -> i instanceof AdminConsoleInterceptor));
    }

    @Test
    void unknownKind() {
        String yaml = """
        apiVersion: membrane-soa.org/v1beta1
        kind: unknownKind
        metadata: { name: x }
        spec: {}
        """;
        Envelope env = new Envelope();
        RuntimeException ex = assertThrows(RuntimeException.class, () -> env.parse(singleDocEvents(yaml),null));
        assertTrue(ex.getMessage().contains("Did not find java class for kind 'unknownKind'"));
    }

    @Test
    void metadataAndTopLevelAdditionalProperties() {
        String yaml = """
        apiVersion: membrane-soa.org/v1beta1
        kind: api
        metadata:
          name: demo
          uid: abc-123
          extra: 1
        x-foo: bar
        spec:
          port: 1000
        """;
        Envelope e = parseEnvelopes(yaml, null).getFirst();
        assertEquals("abc-123", e.getMetadata().getUid());
        assertEquals("1", e.metadata.additionalProperties.get("extra"));
        assertEquals("bar", e.additionalProperties.get("x-foo"));
    }

    @Test
    void noMetadataAndVersion() {
        String yaml = """
        kind: api
        spec:
          port: 1000
        """;
        Envelope e = parseEnvelopes(yaml, null).getFirst();
        APIProxy api = (APIProxy) e.getSpec();
        assertEquals(1000, api.getPort());
    }


    private static List<Envelope> parseEnvelopes(String yaml, BeanRegistry registry) {
        List<Envelope> res = new ArrayList<>();
        for (String doc : splitDocs(yaml)) {
            Iterator<Event> it = singleDocEvents(doc);
            Envelope e = new Envelope();
            e.parse(it, registry);
            res.add(e);
        }
        return res;
    }

    private static List<String> splitDocs(String yaml) {
        return Arrays.stream(yaml.split("(?m)^---\\s*$"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static Iterator<Event> singleDocEvents(String docYaml) {
        Iterable<Event> iterable = new Yaml().parse(new StringReader(docYaml));
        List<Event> filtered = new ArrayList<>();
        for (Event e : iterable) {
            if (e instanceof StreamStartEvent || e instanceof DocumentStartEvent) continue;
            if (e instanceof StreamEndEvent || e instanceof DocumentEndEvent) break;
            filtered.add(e);
        }
        return filtered.iterator();
    }
}
