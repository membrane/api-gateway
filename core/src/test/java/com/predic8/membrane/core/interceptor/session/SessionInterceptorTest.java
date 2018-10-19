package com.predic8.membrane.core.interceptor.session;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.AbstractInterceptorWithSession;
import com.predic8.membrane.core.interceptor.Outcome;
import org.junit.Test;

public class SessionInterceptorTest {

    @Test
    public void sessionV2DevTesting() throws Exception {
        AbstractInterceptorWithSession i = new AbstractInterceptorWithSession() {
            @Override
            public Outcome handleRequest(Exchange exc) throws Exception {
                Session s = getSessionManager().getSession(exc);
                String value = s.get("value");
                if (value == null) {
                    System.out.println("setting");
                    s.put("value", "123456789");
                    value = s.get("value");
                }
                System.out.println(value);
                return Outcome.CONTINUE;
            }

            @Override
            protected Outcome handleResponseInternal(Exchange exc) throws Exception {
                return Outcome.CONTINUE;
            }
        };
        i.init();

        String cookie = "";
        for (int index = 0; index < 2; index++) {
            Request.Builder builder = new Request.Builder().get("/");
            if (!cookie.isEmpty())
                builder.header(Header.COOKIE, cookie);
            Exchange exc = builder.buildExchange();

            i.handleRequest(exc);
            i.handleResponse(exc);
            cookie += exc.getResponse().getHeader().getFirstValue(Header.SET_COOKIE) != null ? exc.getResponse().getHeader().getFirstValue(Header.SET_COOKIE) : "";
        }

    }
}
