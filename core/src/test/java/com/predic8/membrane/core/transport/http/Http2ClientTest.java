package com.predic8.membrane.core.transport.http;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.transport.http.client.HttpClientConfiguration;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class Http2ClientTest {

    @Test
    public void getGoogleHomepage() throws Exception {
        HttpClientConfiguration configuration = new HttpClientConfiguration();
        configuration.setUseExperimentalHttp2(true);
        HttpClient hc = new HttpClient(configuration);
        Exchange e = new Request.Builder().get("https://www.google.de").buildExchange();
        hc.call(e);

        assertEquals(200, e.getResponse().getStatusCode());

        String body = e.getResponse().getBodyAsStringDecoded();
        assertTrue(body.startsWith("<!doctype html>"));
        assertTrue(body.endsWith("</html>"));
    }
}
