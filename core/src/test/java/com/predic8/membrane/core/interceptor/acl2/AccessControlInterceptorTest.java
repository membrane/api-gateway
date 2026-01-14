package com.predic8.membrane.core.interceptor.acl2;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.acl2.rules.Allow;
import com.predic8.membrane.core.interceptor.acl2.rules.Deny;
import com.predic8.membrane.core.router.TestRouter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AccessControlInterceptorTest {

    @Test
    void evaluatePermissionFallthroughNoMatchingRules() {
        Exchange exc = new Request.Builder().buildExchange();
        exc.setRemoteAddr("www.example.com");
        exc.setRemoteAddrIp("192.168.1.100");
        AccessControlInterceptor accessControlInterceptor = new AccessControlInterceptor();
        accessControlInterceptor.init(new TestRouter());
        accessControlInterceptor.setRules(List.of(
                new Allow() {{
                    setTarget("192.168.1.205");
                }},
                new Deny() {{
                    setTarget("bot.example.com");
                }}
        ));

        assertEquals(ABORT, accessControlInterceptor.handleRequest(exc));
    }

    @Test
    void evaluatePermissionAllow() {
        Exchange exc = new Request.Builder().buildExchange();
        exc.setRemoteAddr("www.example.com");
        exc.setRemoteAddrIp("192.168.1.100");
        AccessControlInterceptor accessControlInterceptor = new AccessControlInterceptor();
        accessControlInterceptor.init(new TestRouter());
        accessControlInterceptor.setRules(List.of(
                new Allow() {{
                    setTarget("192.168.1.205");
                }},
                new Deny() {{
                    setTarget("www.example.com");
                }}
        ));

        assertEquals(CONTINUE, accessControlInterceptor.handleRequest(exc));
    }

    @Test
    void evaluatePermissionDeny() {
        Exchange exc = new Request.Builder().buildExchange();
        exc.setRemoteAddr("www.example.com");
        exc.setRemoteAddrIp("192.168.1.100");
        AccessControlInterceptor accessControlInterceptor = new AccessControlInterceptor();
        accessControlInterceptor.init(new TestRouter());
        accessControlInterceptor.setRules(List.of(
                new Deny() {{
                    setTarget("192.168.1.100");
                }},
                new Allow() {{
                    setTarget("bot.example.com");
                }}
        ));

        assertEquals(ABORT, accessControlInterceptor.handleRequest(exc));
    }
}