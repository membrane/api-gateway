package com.predic8.membrane.core.interceptor.session;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptorWithSession;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;

public class SessionInterceptorTest {

    private HttpRouter router;

    @Before
    public void setUp() {
        router = new HttpRouter();
    }

    @Test
    public void generalSessionUsageTest() throws Exception {
        Rule rule = new ServiceProxy(new ServiceProxyKey("localhost", "*", ".*", 3001), "thomas-bayer.com", 80);
        router.getRuleManager().addProxyAndOpenPortIfNew(rule);

        AtomicLong counter = new AtomicLong(0);
        List<Long> vals = new ArrayList<>();

        AbstractInterceptorWithSession interceptor = new AbstractInterceptorWithSession() {
            @Override
            public Outcome handleRequest(Exchange exc) throws Exception {
                Session s = getSessionManager().getSession(exc);
                vals.add(s.get("value"));
                long nextVal = counter.getAndIncrement();
                s.put("value", nextVal);
                vals.add(s.get("value"));
                return Outcome.CONTINUE;
            }

            @Override
            protected Outcome handleResponseInternal(Exchange exc) throws Exception {
                return Outcome.CONTINUE;
            }
        };

        router.addUserFeatureInterceptor(interceptor);
        router.init();

        CloseableHttpClient httpClient = HttpClients.createDefault();
        IntStream.range(0, 50).forEach(i -> {
            HttpGet httpGet = new HttpGet("http://localhost:3001");
            try {
                CloseableHttpResponse response = httpClient.execute(httpGet);

                try {
                    EntityUtils.consume(response.getEntity());
                } finally {
                    response.close();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        assertEquals(null,vals.get(0));
        for(int i = 1; i < 98; i+=2){
            int index = Math.round((i-1)/2f);
            assertEquals(index,vals.get(i).intValue());
        }
        assertEquals(49,vals.get(99).intValue());
    }

    @After
    public void tearDown() throws IOException {
        router.shutdown();
    }
}
