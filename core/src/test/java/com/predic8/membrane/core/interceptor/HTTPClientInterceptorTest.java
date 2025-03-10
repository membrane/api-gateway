package com.predic8.membrane.core.interceptor;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.proxies.NullProxy;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;

import static org.junit.jupiter.api.Assertions.*;

class HTTPClientInterceptorTest {

    @Test
    public void testProtocolUpgradeRejected() throws URISyntaxException {
        Router r = new Router();

        HTTPClientInterceptor hci = new HTTPClientInterceptor();
        hci.init(r);

        Exchange e = Request.get("http://localhost:2000/")
                .header("Connection", "upgrade")
                .header("Upgrade", "rejected")
                .buildExchange();
        e.setProxy(new NullProxy());

        hci.handleRequest(e);

        assertEquals(401, e.getResponse().getStatusCode());
    }

}