package com.predic8.membrane.core.interceptor.acl2;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.acl2.rules.Allow;
import com.predic8.membrane.core.interceptor.acl2.rules.Deny;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AccessControlInterceptorTest {

    @Test
    void evaluatePermissionFallthroughNoRules() {
        Exchange exc = new Request.Builder().buildExchange();
        exc.setRemoteAddr("www.example.com");
        exc.setRemoteAddrIp("192.168.1.100");
        AccessControlInterceptor accessControlInterceptor = new AccessControlInterceptor();

        assertEquals(
                Optional.empty(),
                accessControlInterceptor.evaluatePermission(
                        exc,
                        new ArrayList<>()
                )
        );
    }

    @Test
    void evaluatePermissionFallthroughNoMatchingRules() {
        Exchange exc = new Request.Builder().buildExchange();
        exc.setRemoteAddr("www.example.com");
        exc.setRemoteAddrIp("192.168.1.100");
        AccessControlInterceptor accessControlInterceptor = new AccessControlInterceptor();

        assertEquals(
                Optional.empty(),
                accessControlInterceptor.evaluatePermission(
                        exc,
                        List.of(
                                new Allow() {{setTarget("192.168.1.205");}},
                                new Deny() {{setTarget("bot.example.com");}}
                        )
                )
        );
    }

    @Test
    void evaluatePermissionAllow() {
        Exchange exc = new Request.Builder().buildExchange();
        exc.setRemoteAddr("www.example.com");
        exc.setRemoteAddrIp("192.168.1.100");
        AccessControlInterceptor accessControlInterceptor = new AccessControlInterceptor();

        assertEquals(
                Optional.of(TRUE),
                accessControlInterceptor.evaluatePermission(
                        exc,
                        List.of(
                                new Allow() {{setTarget("192.168.1.205");}},
                                new Deny() {{setTarget("www.example.com");}}
                        )
                )
        );
    }

    @Test
    void evaluatePermissionDeny() {
        Exchange exc = new Request.Builder().buildExchange();
        exc.setRemoteAddr("www.example.com");
        exc.setRemoteAddrIp("192.168.1.100");
        AccessControlInterceptor accessControlInterceptor = new AccessControlInterceptor();

        assertEquals(
                Optional.of(FALSE),
                accessControlInterceptor.evaluatePermission(
                        exc,
                        List.of(
                                new Deny() {{setTarget("192.168.1.100");}},
                                new Allow() {{setTarget("bot.example.com");}}
                        )
                )
        );
    }
}