/* Copyright 2019 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.session;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.type.*;
import com.fasterxml.jackson.databind.*;
import com.google.common.collect.*;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.proxies.*;
import org.apache.commons.io.*;
import org.apache.http.client.config.*;
import org.apache.http.client.methods.*;
import org.apache.http.impl.client.*;
import org.apache.http.impl.conn.*;
import org.apache.http.util.*;
import org.junit.jupiter.api.*;

import java.io.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.stream.*;

import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static java.nio.charset.StandardCharsets.*;
import static org.junit.jupiter.api.Assertions.*;

public class SessionInterceptorTest {

    private HttpRouter router;
    private CloseableHttpClient httpClient;

    @BeforeEach
    public void setUp() {
        router = new HttpRouter();
        httpClient = createHttpClient();
    }

    @AfterEach
    void shutDown() throws IOException {
        router.shutdown();
        httpClient.close();
    }

    @Test
    public void generalSessionUsageTest() throws Exception {
        ServiceProxy sp = createTestServiceProxy();
        router.getRuleManager().addProxyAndOpenPortIfNew(sp);

        AtomicLong counter = new AtomicLong(0);
        List<Long> vals = new ArrayList<>();

        AbstractInterceptorWithSession interceptor = defineInterceptor(counter,vals);

        sp.getInterceptors().add(interceptor);
        sp.getInterceptors().add(testResponseInterceptor());

        router.init();

        IntStream.range(0, 50).forEach(i -> sendRequest());

        assertNull(vals.getFirst());
        for(int i = 1; i < 98; i+=2){
            int index = Math.round((i-1)/2f);
            assertEquals(index,vals.get(i).intValue());
        }
        assertEquals(49,vals.get(99).intValue());
    }

    private ServiceProxy createTestServiceProxy() {
        return new ServiceProxy(new ServiceProxyKey("localhost", "*", ".*", 3001), "api.predic8.de", 80);
    }

    @Test
    public void expirationTest() throws Exception{
        ServiceProxy sp = createTestServiceProxy();
        router.getRuleManager().addProxyAndOpenPortIfNew(sp);

        AtomicLong counter = new AtomicLong(0);
        List<Long> vals = new ArrayList<>();

        AbstractInterceptorWithSession interceptor = defineInterceptor(counter, vals);

        sp.getInterceptors().add(interceptor);
        sp.getInterceptors().add(testResponseInterceptor());

        router.init();

        interceptor.getSessionManager().setExpiresAfterSeconds(0);

        IntStream.range(0, 50).forEach(i -> sendRequest());

        for(int i = 0; i < 100; i+=2)
            assertNull(vals.get(i));
    }

    @Test
    public void noUnneededRenewalOnReadOnlySession() throws Exception{
        ServiceProxy sp = createTestServiceProxy();
        router.getRuleManager().addProxyAndOpenPortIfNew(sp);

        sp.getInterceptors().add(createAndReadOnlySessionInterceptor());
        sp.getInterceptors().add(testResponseInterceptor());

        router.init();

        List<Map<String,Object>> bodies = new ArrayList<>();

        int lowerBound = 0;
        int upperBound = 1000;

        IntStream.range(lowerBound, upperBound).forEach(i -> bodies.add(sendRequest()));

        IntStream.range(lowerBound+1,upperBound-1).forEach(i -> assertEquals(bodies.get(i),bodies.get(i+1)));
    }

    @Disabled
    @Test
    public void renewalOnReadOnlySession() throws Exception{
        ServiceProxy sp = createTestServiceProxy();
        AbstractInterceptorWithSession createAndReadOnlySessionInterceptor = createAndReadOnlySessionInterceptor();

        sp.getInterceptors().add(createAndReadOnlySessionInterceptor);
        sp.getInterceptors().add(testResponseInterceptor());

        router.getRuleManager().addProxyAndOpenPortIfNew(sp);
        router.init();

        List<Map<String,Object>> bodies = new ArrayList<>();

        int lowerBound = 0;
        int upperBound = 10;

        IntStream.range(lowerBound, upperBound).forEach(i -> bodies.add(sendRequest()));

        bodies.forEach(this::printCookie);

        IntStream.range(lowerBound+1,upperBound-1).forEach(i -> assertEquals(getCookieKey(bodies.get(i)),getCookieKey(bodies.get(i+1))));

        String cookieOne = getCookieKey(bodies.get(upperBound-1));

        Duration origRenewalTime = ((JwtSessionManager)createAndReadOnlySessionInterceptor.getSessionManager()).getRenewalTime();

        ((JwtSessionManager)createAndReadOnlySessionInterceptor.getSessionManager()).setRenewalTime(Duration.ofMillis(0));
        sendRequest();
        ((JwtSessionManager)createAndReadOnlySessionInterceptor.getSessionManager()).setRenewalTime(origRenewalTime);

        bodies.clear();

        IntStream.range(lowerBound, upperBound).forEach(i -> bodies.add(sendRequest()));

        bodies.forEach(this::printCookie);

        IntStream.range(lowerBound+1,upperBound-1).forEach(i -> assertEquals(getCookieKey(bodies.get(i)),getCookieKey(bodies.get(i+1))));

        String cookieTwo = getCookieKey(bodies.get(upperBound-1));
        assertNotEquals(cookieOne,cookieTwo);

    }

    public void printCookie(Map body){
        System.out.println(getCookieKey(body));
    }

    public String getCookieKey(Map cookie){
        String raw = ((Map<?, ?>)cookie.get("request")).get("Cookie").toString();
        return raw.split("=")[0];
    }

    private AbstractInterceptor testResponseInterceptor() {
        return new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) {
                if(exc.getResponse() == null)
                    exc.setResponse(Response.ok().build());
                try {
                    exc.getResponse().setBodyContent(createTestResponseBody(exc).getBytes());
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
                return RETURN;
            }

            @Override
            public Outcome handleResponse(Exchange exc) {
                return RETURN;
            }
        };
    }

    private AbstractInterceptorWithSession createAndReadOnlySessionInterceptor() {
        return new AbstractInterceptorWithSession() {
            @Override
            protected Outcome handleRequestInternal(Exchange exc) {
                getSessionManager().getSession(exc);
                return CONTINUE;
            }

            @Override
            protected Outcome handleResponseInternal(Exchange exc) {
                return CONTINUE;
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

        ImmutableMap<String, Map> value = ImmutableMap.of("request", request, "response", response);
        return new ObjectMapper().writeValueAsString(value);
    }

    private Stream<HeaderField> getHeader(String name, Message msg){
        if(msg == null)
            return Stream.empty();
        return Stream.of(msg.getHeader().getAllHeaderFields())
                .filter(hf -> hf.getHeaderName().getName().equalsIgnoreCase(name));
    }

    private void addJoinedHeaderTo(Map cache, String name, Message msg){
        cache.put(name, getHeader(name,msg)
                .map(HeaderField::getValue)
                .collect(Collectors.joining(";")));
    }

    private static CloseableHttpClient createHttpClient() {
        return HttpClients.custom().setConnectionManager(new BasicHttpClientConnectionManager()).setDefaultRequestConfig(RequestConfig.custom().setCookieSpec(CookieSpecs.STANDARD).build()).build();
    }

    private Map<String,Object> sendRequest() {
        Map<String, Object> result;
        HttpGet httpGet = new HttpGet("http://localhost:3001");
        try {
            try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                String string = IOUtils.toString(response.getEntity().getContent(), UTF_8);
                result = new ObjectMapper().readValue(string, new TypeReference<>() {});
                EntityUtils.consume(response.getEntity());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    private AbstractInterceptorWithSession defineInterceptor(AtomicLong counter, List<Long> vals) {
        return new AbstractInterceptorWithSession() {
                @Override
                public Outcome handleRequestInternal(Exchange exc) {
                    Session s = getSessionManager().getSession(exc);
                    vals.add(s.get("value"));
                    long nextVal = counter.getAndIncrement();
                    s.put("value", nextVal);
                    vals.add(s.get("value"));
                    return CONTINUE;
                }

                @Override
                protected Outcome handleResponseInternal(Exchange exc) {
                    return CONTINUE;
                }
            };
    }
}
