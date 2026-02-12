package com.predic8.membrane.core.http;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.flow.ReturnInterceptor;
import com.predic8.membrane.core.interceptor.log.LogInterceptor;
import com.predic8.membrane.core.openapi.serviceproxy.APIProxy;
import com.predic8.membrane.core.router.DefaultRouter;
import com.predic8.membrane.core.transport.http.HttpTransport;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import static io.restassured.RestAssured.given;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatchWithoutBodyTest {

    private DefaultRouter router;

    AtomicInteger requestSuccessfullyProcessed = new AtomicInteger(0);

    @BeforeEach
    void setUp() throws Exception {
        router = createOpenApiProxy();
    }

    @AfterEach
    void tearDown() {
        if (router != null) router.stop();
    }

    @Test
    void patch_really_no_body() throws Exception {
        String resp = rawPatchNoBody("localhost", 2000, "/items/1");
        System.out.println(resp);
        assertTrue(resp.startsWith("HTTP/1.1 "), resp);
        assertEquals(1, requestSuccessfullyProcessed.get(), "HTTP Request was not successfully processed. This indicates a read timeout has happened.");
    }

    static String rawPatchNoBody(String host, int port, String path) throws Exception {
        try (Socket s = new Socket(host, port)) {
            byte[] req = ("""
                PATCH %s HTTP/1.1\r
                Host: %s:%d\r
                Connection: close\r
                \r
                """.formatted(path, host, port))
                    .getBytes(US_ASCII);

            s.getOutputStream().write(req);
            s.getOutputStream().flush();

            return new String(s.getInputStream().readAllBytes(), US_ASCII);
        }
    }

    private DefaultRouter createOpenApiProxy() throws Exception {
        DefaultRouter r = new DefaultRouter();
        r.setTransport(createHttpTransport());
        r.add(createApiProxy());
        r.start();
        return r;
    }

    private static @NotNull HttpTransport createHttpTransport() {
        HttpTransport transport = new HttpTransport();
        transport.setSocketTimeout(2000);
        return transport;
    }

    private @NotNull APIProxy createApiProxy() {
        APIProxy apiProxy = new APIProxy();
        apiProxy.setPort(2000);
        apiProxy.setFlow(new ArrayList<>(Arrays.asList(
                createLogInterceptor(),
                new AbstractInterceptor() {
                    @Override
                    public Outcome handleRequest(Exchange exc) {
                        requestSuccessfullyProcessed.set(1);
                        return Outcome.CONTINUE;
                    }
                },
                new ReturnInterceptor())));
        return apiProxy;
    }

    private static @NotNull LogInterceptor createLogInterceptor() {
        LogInterceptor li = new LogInterceptor();
        li.setBody(true);
        return li;
    }

}