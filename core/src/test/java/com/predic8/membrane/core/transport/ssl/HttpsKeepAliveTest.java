package com.predic8.membrane.core.transport.ssl;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.config.security.KeyStore;
import com.predic8.membrane.core.config.security.SSLParser;
import com.predic8.membrane.core.config.security.TrustStore;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.resolver.ResolverMap;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.transport.http.HttpClient;
import com.predic8.membrane.core.transport.http.HttpServerHandler;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ConcurrentHashMap;

import static com.predic8.membrane.core.exchange.Exchange.SSL_CONTEXT;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class HttpsKeepAliveTest {

    private static HttpRouter server;

    private static ConcurrentHashMap<String, Boolean> connectionHashes = new ConcurrentHashMap<>();

    @BeforeAll
    public static void startServer() {
        server = new HttpRouter();
        server.setHotDeploy(false);
        ServiceProxy sp = new ServiceProxy();
        sp.setPort(3063);
        SSLParser sslIB = new SSLParser();
        KeyStore ksIB = new KeyStore();
        ksIB.setLocation("classpath:/ssl-rsa.keystore");
        ksIB.setKeyPassword("secret");
        sslIB.setKeyStore(ksIB);
        sp.setSslInboundParser(sslIB);
        sp.getInterceptors().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) {
                try {
                    exc.setResponse(Response.ok("ssltest").build());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                connectionHashes.put("" + ((HttpServerHandler)exc.getHandler()).getSrcOut().hashCode(), true);
                return RETURN;
            }
        });
        server.getRules().add(sp);
        server.start();
    }

    private static StaticSSLContext createSSLOutboundContext() {
        SSLParser sslOB = new SSLParser();
        sslOB.setEndpointIdentificationAlgorithm("");
        TrustStore tsOB = new TrustStore();
        tsOB.setLocation("classpath:/ssl-rsa-pub.keystore");
        tsOB.setPassword("secret");
        sslOB.setTrustStore(tsOB);
        return new StaticSSLContext(sslOB, new ResolverMap(), "/");
    }

    @AfterAll
    public static void shutdownServer() {
        server.stop();
    }

    @Test
    public void httpsKeepAlive() throws Exception {
        SSLContext sslCtxOb = createSSLOutboundContext();
        try (HttpClient hc = new HttpClient()) {
            for (int i = 0; i < 2; i++) {
                Exchange exc1 = new Request.Builder().get("https://localhost:3063/").buildExchange();
                exc1.setProperty(SSL_CONTEXT, sslCtxOb);
                hc.call(exc1);

                assertEquals(200, exc1.getResponse().getStatusCode());
                assertEquals("ssltest", exc1.getResponse().getBodyAsStringDecoded());
            }
        }

        assertEquals(1, connectionHashes.size());
    }
}
