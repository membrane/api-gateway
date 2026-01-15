package com.predic8.membrane.core.interceptor.acl;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.acl.rules.AccessRule;
import com.predic8.membrane.core.interceptor.acl.rules.Allow;
import com.predic8.membrane.core.interceptor.acl.rules.Deny;
import com.predic8.membrane.core.proxies.Proxy;
import com.predic8.membrane.core.router.TestRouter;
import com.predic8.membrane.core.util.ConfigurationException;
import com.predic8.membrane.core.util.DNSCache;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.List;

import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AccessControlInterceptorTest {

    private static final class RouterWithDns extends TestRouter {
        private final DNSCache dns;

        private RouterWithDns(DNSCache dns) {
            this.dns = dns;
        }

        @Override
        public DNSCache getDnsCache() {
            return dns;
        }
    }

    private static Allow allow(String target) {
        Allow a = new Allow();
        a.setTarget(target);
        return a;
    }

    private static Deny deny(String target) {
        Deny d = new Deny();
        d.setTarget(target);
        return d;
    }

    private static Exchange exc(String remoteIp) {
        Exchange exc = new Request.Builder().buildExchange();
        exc.setRemoteAddr("client");
        exc.setRemoteAddrIp(remoteIp);
        exc.setOriginalRequestUri("/foo");
        return exc;
    }

    private static AccessControlInterceptor interceptor(DNSCache dns, List<AccessRule> rules) {
        AccessControlInterceptor i = new AccessControlInterceptor();
        i.setRules(rules);
        i.init(new RouterWithDns(dns), mock(Proxy.class));
        return i;
    }

    @Test
    void init_rejects_no_rules() {
        DNSCache dns = mock(DNSCache.class);

        AccessControlInterceptor i = new AccessControlInterceptor();
        i.setRules(List.of());

        assertThrows(ConfigurationException.class, () -> i.init(new RouterWithDns(dns), mock(Proxy.class)));
    }

    @Test
    void denies_when_no_rule_matches_default_deny() {
        assertEquals(ABORT, interceptor(mock(DNSCache.class), List.of(allow("10.0.0.0/8"))).handleRequest(exc("192.168.1.100")));
    }

    @Test
    void allows_when_first_matching_rule_allows() {
        assertEquals(
                CONTINUE,
                interceptor(mock(DNSCache.class), List.of(
                        allow("192.168.1.0/24"),
                        deny("0.0.0.0/0")
                )).handleRequest(exc("192.168.1.100"))
        );
    }

    @Test
    void denies_when_first_matching_rule_denies_even_if_later_allows() {
        assertEquals(
                ABORT,
                interceptor(mock(DNSCache.class), List.of(
                        deny("192.168.1.0/24"),
                        allow("0.0.0.0/0")
                )).handleRequest(exc("192.168.1.100"))
        );
    }

    @Test
    void trims_target_value_via_rule_setter() {
        AccessControlInterceptor i = interceptor(mock(DNSCache.class), List.of(
                allow(" 10.0.0.0/8 ")
        ));

        assertEquals(CONTINUE, i.handleRequest(exc("10.123.45.67")));
        assertEquals(ABORT, i.handleRequest(exc("11.0.0.1")));
    }

    @Test
    void denies_when_remote_ip_is_blank_or_invalid() {
        AccessControlInterceptor i = interceptor(mock(DNSCache.class), List.of(
                allow("0.0.0.0/0")
        ));

        assertEquals(ABORT, i.handleRequest(exc("")));
        assertEquals(ABORT, i.handleRequest(exc("   ")));
        assertEquals(ABORT, i.handleRequest(exc("not-an-ip")));
    }

    @Test
    void allows_ipv6_cidr_match() {
        AccessControlInterceptor i = interceptor(mock(DNSCache.class), List.of(
                allow("2001:db8::/64")
        ));

        assertEquals(CONTINUE, i.handleRequest(exc("2001:db8::1")));
        assertEquals(ABORT, i.handleRequest(exc("2001:db9::1")));
    }

    @Test
    void hostname_rule_triggers_dns_and_allows_on_match() {
        DNSCache dns = mock(DNSCache.class);
        when(dns.getCanonicalHostName(any(InetAddress.class))).thenReturn("www.example.com");

        AccessControlInterceptor i = interceptor(dns, List.of(
                allow("^www\\.example\\.com$")
        ));

        assertEquals(CONTINUE, i.handleRequest(exc("1.2.3.4")));
        verify(dns, times(1)).getCanonicalHostName(any(InetAddress.class));
    }

    @Test
    void hostname_rule_triggers_dns_and_denies_on_mismatch_default_deny() {
        DNSCache dns = mock(DNSCache.class);
        when(dns.getCanonicalHostName(any(InetAddress.class))).thenReturn("bot.example.com");

        AccessControlInterceptor i = interceptor(dns, List.of(
                allow("^www\\.example\\.com$")
        ));

        assertEquals(ABORT, i.handleRequest(exc("1.2.3.4")));
        verify(dns, times(1)).getCanonicalHostName(any(InetAddress.class));
    }

    @Test
    void dns_is_not_used_when_no_hostname_rules_present() {
        DNSCache dns = mock(DNSCache.class);

        AccessControlInterceptor i = interceptor(dns, List.of(
                allow("0.0.0.0/0")
        ));

        assertEquals(CONTINUE, i.handleRequest(exc("1.2.3.4")));
        verify(dns, never()).getCanonicalHostName(any(InetAddress.class));
    }

    @Test
    void first_decision_wins_across_mixed_target_types() {
        DNSCache dns = mock(DNSCache.class);
        when(dns.getCanonicalHostName(any(InetAddress.class))).thenReturn("www.example.com");

        AccessControlInterceptor i = interceptor(dns, List.of(
                deny("^www\\.example\\.com$"),
                allow("0.0.0.0/0")
        ));

        assertEquals(ABORT, i.handleRequest(exc("1.2.3.4")));
        verify(dns, times(1)).getCanonicalHostName(any(InetAddress.class));
    }
}
