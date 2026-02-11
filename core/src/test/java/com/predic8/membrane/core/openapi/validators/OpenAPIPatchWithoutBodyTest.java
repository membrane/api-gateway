package com.predic8.membrane.core.openapi.validators;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.interceptor.flow.ReturnInterceptor;
import com.predic8.membrane.core.openapi.serviceproxy.APIProxy;
import com.predic8.membrane.core.openapi.serviceproxy.OpenAPISpec;
import com.predic8.membrane.core.transport.http.HttpTransport;
import com.predic8.membrane.core.util.URIFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.predic8.membrane.core.openapi.serviceproxy.OpenAPISpec.YesNoOpenAPIOption.YES;
import static com.predic8.membrane.test.TestUtil.getPathFromResource;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAPIPatchWithoutBodyTest {

    private Router router;
    private Router backend;

    @BeforeEach
    void setUp() throws Exception {
        backend = createBackend();
        router = createOpenApiProxy();
    }

    @AfterEach
    void tearDown() {
        if (router != null) router.shutdown();
        if (backend != null) backend.shutdown();
    }

    @Test
    void patch_without_body() {
        var res = given().patch("http://localhost:2000/items/1");
        System.out.println(res.body().asString());
    }

    @Test
    void patch_really_no_body() throws Exception {
        String resp = rawPatchNoBody("localhost", 2000, "/items/1");
        System.out.println(resp);
        assertTrue(resp.startsWith("HTTP/1.1 "), resp);
    }

    static String rawPatchNoBody(String host, int port, String path) throws Exception {
        try (Socket s = new Socket(host, port)) {
            byte[] req = ("""
                PATCH %s HTTP/1.1\r
                Host: %s:%d\r
                Connection: close\r
                \r
                """.formatted(path, host, port))
                    .getBytes(StandardCharsets.US_ASCII);

            s.getOutputStream().write(req);
            s.getOutputStream().flush();

            return readAll(s.getInputStream());
        }
    }

    static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        for (int n; (n = in.read(buf)) != -1; ) baos.write(buf, 0, n);
        return baos.toString(StandardCharsets.UTF_8);
    }


    private static Router createBackend() throws Exception {
        Router r = new Router();
        r.setTransport(new HttpTransport());

        APIProxy backendProxy = new APIProxy();
        backendProxy.setPort(3000);
        backendProxy.setFlow(List.of(new ReturnInterceptor()));

        r.getRuleManager().addProxyAndOpenPortIfNew(backendProxy);
        r.init();
        return r;
    }

    private static Router createOpenApiProxy() throws Exception {
        Router r = new Router();
        r.setTransport(new HttpTransport());
        r.setUriFactory(new URIFactory());

        APIProxy apiProxy = new APIProxy();
        apiProxy.setPort(2000);
        apiProxy.setSpecs(List.of(new OpenAPISpec() {{
            setLocation(getPathFromResource("openapi/specs/patch.yaml"));
            setValidateRequests(YES);
        }}));

        r.getRuleManager().addProxyAndOpenPortIfNew(apiProxy);
        r.init();
        return r;
    }

}
