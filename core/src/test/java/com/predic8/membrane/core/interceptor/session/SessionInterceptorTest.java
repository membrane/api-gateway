package com.predic8.membrane.core.interceptor.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.HeaderField;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.AbstractInterceptorWithSession;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;

public class SessionInterceptorTest {

    private HttpRouter router;
    private CloseableHttpClient httpClient;

    @Before
    public void setUp() {
        router = new HttpRouter();
        httpClient = createHttpClient();
    }

    @Test
    public void generalSessionUsageTest() throws Exception {
        router.getRuleManager().addProxyAndOpenPortIfNew(createTestServiceProxy());

        AtomicLong counter = new AtomicLong(0);
        List<Long> vals = new ArrayList<>();

        AbstractInterceptorWithSession interceptor = defineInterceptor(counter,vals);

        router.addUserFeatureInterceptor(interceptor);
        router.addUserFeatureInterceptor(testResponseInterceptor());
        router.init();

        IntStream.range(0, 50).forEach(i -> sendRequest());

        assertEquals(null,vals.get(0));
        for(int i = 1; i < 98; i+=2){
            int index = Math.round((i-1)/2f);
            assertEquals(index,vals.get(i).intValue());
        }
        assertEquals(49,vals.get(99).intValue());
    }

    private ServiceProxy createTestServiceProxy() {
        return new ServiceProxy(new ServiceProxyKey("localhost", "*", ".*", 3001), "thomas-bayer.com", 80);
    }

    @Test
    public void expirationTest() throws Exception{
        router.getRuleManager().addProxyAndOpenPortIfNew(createTestServiceProxy());

        AtomicLong counter = new AtomicLong(0);
        List<Long> vals = new ArrayList<>();

        AbstractInterceptorWithSession interceptor = defineInterceptor(counter, vals);

        router.addUserFeatureInterceptor(interceptor);
        router.addUserFeatureInterceptor(testResponseInterceptor());
        router.init();

        interceptor.getSessionManager().setExpiresAfterSeconds(0);

        IntStream.range(0, 50).forEach(i -> sendRequest());

        for(int i = 0; i < 100; i+=2)
            assertEquals(null,vals.get(i));
    }

    @Test
    public void noRenewalOnReadOnlySession() throws Exception{
        router.getRuleManager().addProxyAndOpenPortIfNew(createTestServiceProxy());

        router.addUserFeatureInterceptor(createAndReadOnlySessionInterceptor());
        router.addUserFeatureInterceptor(testResponseInterceptor());
        router.init();

        List<Map<String,Object>> bodies = new ArrayList<>();

        int lowerBound = 0;
        int upperBound = 100;

        IntStream.range(lowerBound, upperBound).forEach(i -> bodies.add(sendRequest()));

        IntStream.range(lowerBound+1,upperBound-1).forEach(i -> {
            assertEquals(bodies.get(i),bodies.get(i+1));
        });
    }

    private AbstractInterceptor testResponseInterceptor() {
        return new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) throws Exception {
                exc.setResponse(Response.ok(createTestResponseBody(exc)).build());
                return Outcome.RETURN;
            }

            @Override
            public Outcome handleResponse(Exchange exc) throws Exception {
                return Outcome.RETURN;
            }
        };
    }

    private AbstractInterceptorWithSession createAndReadOnlySessionInterceptor() {
        return new AbstractInterceptorWithSession() {
            @Override
            protected Outcome handleRequestInternal(Exchange exc) throws Exception {
                getSessionManager().getSession(exc);
                return Outcome.CONTINUE;
            }

            @Override
            protected Outcome handleResponseInternal(Exchange exc) throws Exception {
                return Outcome.CONTINUE;
            }
        };
    }

    private String createTestResponseBody(Exchange exc) throws JsonProcessingException {
        Map request = new HashMap();
        Map response = new HashMap();

        Stream.of("Cookie")
                .forEach(cookieName -> addJoinedHeaderTo(request,cookieName,exc.getRequest()));

        Stream.of("Set-Cookie")
                .forEach(cookieName -> addJoinedHeaderTo(response,cookieName,exc.getResponse()));

        return new ObjectMapper().writeValueAsString(ImmutableMap.of("request",request,"response",response));
    }

    private Stream<HeaderField> getHeader(String name, Message msg){
        if(msg == null)
            return Stream.empty();
        return Stream.of(msg.getHeader().getAllHeaderFields())
                .filter(hf -> hf.getHeaderName().equals(name));
    }

    private void addJoinedHeaderTo(Map cache, String name, Message msg){
        cache.put(name, getHeader(name,msg)
                .map(hf -> hf.getValue())
                .collect(Collectors.joining(";")));
    }

    private CloseableHttpClient createHttpClient() {
        return HttpClients.custom().setDefaultRequestConfig(RequestConfig.custom().setCookieSpec(CookieSpecs.STANDARD).build()).build();
    }

    private Map<String,Object> sendRequest() {
        Map result;
        HttpGet httpGet = new HttpGet("http://localhost:3001");
        try {
            CloseableHttpResponse response = httpClient.execute(httpGet);
            try {
                String body = IOUtils.toString(response.getEntity().getContent());
                result = new ObjectMapper().readValue(body,Map.class);
                EntityUtils.consume(response.getEntity());
            } finally {
                response.close();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    private AbstractInterceptorWithSession defineInterceptor(AtomicLong counter, List<Long> vals) {
        return new AbstractInterceptorWithSession() {
                @Override
                public Outcome handleRequestInternal(Exchange exc) throws Exception {
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
    }

    @After
    public void tearDown() throws IOException {
        httpClient.close();
        router.shutdown();
    }
}
