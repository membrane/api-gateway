package com.predic8.membrane.core.transport.http;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.config.security.KeyStore;
import com.predic8.membrane.core.config.security.SSLParser;
import com.predic8.membrane.core.config.security.TrustStore;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;
import com.predic8.membrane.core.transport.http.client.HttpClientConfiguration;
import com.predic8.membrane.core.transport.ssl.SSLContext;
import com.predic8.membrane.core.transport.ssl.StaticSSLContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;

import static com.predic8.membrane.core.exchange.Exchange.SSL_CONTEXT;
import static com.predic8.membrane.core.transport.http.HttpClient.HTTP2;
import static org.junit.Assert.*;

public class Http2ClientServerTest {
    private volatile Response response;
    private HttpClient hc;
    private HttpRouter router;

    @Before
    public void setup() throws Exception {
        SSLParser sslParser = new SSLParser();
        sslParser.setUseExperimentalHttp2(true);
        sslParser.setEndpointIdentificationAlgorithm("");
        sslParser.setShowSSLExceptions(true);
        sslParser.setKeyStore(new KeyStore());
        sslParser.getKeyStore().setLocation("classpath:/ssl-rsa.keystore");
        sslParser.getKeyStore().setKeyPassword("secret");

        router = new HttpRouter();
        router.setHotDeploy(false);
        ServiceProxy sp = new ServiceProxy(new ServiceProxyKey(3049), "localhost", 80);
        sp.setSslInboundParser(sslParser);
        sp.getInterceptors().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) throws Exception {
                exc.setResponse(response);
                return Outcome.RETURN;
            }
        });
        router.getRules().add(sp);
        router.start();


        SSLParser sslParser2 = new SSLParser();
        sslParser2.setEndpointIdentificationAlgorithm("");
        sslParser2.setShowSSLExceptions(true);
        sslParser2.setTrustStore(new TrustStore());
        sslParser2.getTrustStore().setLocation("classpath:/ssl-rsa-pub.keystore");
        sslParser2.getTrustStore().setPassword("secret");

        HttpClientConfiguration configuration = new HttpClientConfiguration();
        configuration.setUseExperimentalHttp2(true);
        configuration.setSslParser(sslParser2);
        configuration.setBaseLocation("/");
        hc = new HttpClient(configuration);
    }

    @After
    public void done() {
        router.stop();
    }

    @Test
    public void simple() throws Exception {
        test200("here");
    }

    @Test
    public void longBody() throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++)
            sb.append("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789+!@#$%^&*(){}?+S_|");
        // 80k exceeds the max frame size as well as the initial window size
        test200(sb.toString());
    }

    private void test200(String body) throws Exception {
        Response r = transferViaHttp2(Response.ok(body).build());

        assertEquals(200, r.getStatusCode());
        assertEquals(body, r.getBodyAsStringDecoded());
    }

    private Response transferViaHttp2(Response response) throws Exception {
        this.response = response;

        Exchange e = new Request.Builder().get("https://localhost:3049").buildExchange();
        hc.call(e);

        assertNotNull(e.getProperty(HTTP2));

        return e.getResponse();
    }
}
