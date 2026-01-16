/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.acl;

import com.predic8.membrane.core.interceptor.acl.rules.Allow;
import com.predic8.membrane.core.interceptor.acl.rules.Deny;
import com.predic8.membrane.core.router.Router;
import com.predic8.membrane.core.util.ConfigurationException;
import com.predic8.membrane.core.util.DNSCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AccessControlTest {

    private Router router;
    private DNSCache dnsCache;
    private AccessControl acl;

    @BeforeEach
    void setUp() {
        router = mock(Router.class);
        dnsCache = mock(DNSCache.class);
        when(router.getDnsCache()).thenReturn(dnsCache);

        acl = new AccessControl();
    }

    @Test
    void init_rejects_empty_rules() {
        acl.setRules(List.of());
        assertThrows(ConfigurationException.class, () -> acl.init(router.getDnsCache()));
    }

    @Test
    void isPermitted_returns_false_for_null_blank_or_invalid_ip() {
        Allow allow = new Allow();
        allow.setTarget("0.0.0.0/0");
        acl.setRules(List.of(allow));
        acl.init(router.getDnsCache());

        assertFalse(acl.isPermitted(null));
        assertFalse(acl.isPermitted(""));
        assertFalse(acl.isPermitted("   "));
        assertFalse(acl.isPermitted("not-an-ip"));
    }

    @Test
    void isPermitted_allows_when_matching_allow_rule() {
        Allow allow = new Allow();
        allow.setTarget("10.0.0.0/8");

        acl.setRules(List.of(allow));
        acl.init(router.getDnsCache());

        assertTrue(acl.isPermitted("10.123.45.67"));
        assertFalse(acl.isPermitted("11.0.0.1"));
    }

    @Test
    void isPermitted_denies_when_matching_deny_rule() {
        Deny deny = new Deny();
        deny.setTarget("10.0.0.0/8");

        acl.setRules(List.of(deny));
        acl.init(router.getDnsCache());

        assertFalse(acl.isPermitted("10.123.45.67"));
        assertFalse(acl.isPermitted("11.0.0.1"));
    }

    @Test
    void isPermitted_first_decision_wins() {
        Deny deny = new Deny();
        deny.setTarget("10.0.0.0/8");

        Allow allow = new Allow();
        allow.setTarget("10.0.0.0/8");

        acl.setRules(List.of(deny, allow));
        acl.init(router.getDnsCache());

        assertFalse(acl.isPermitted("10.1.2.3"));
    }

    @Test
    void isPermitted_no_rule_matches_defaults_to_deny() {
        Allow allow = new Allow();
        allow.setTarget("10.0.0.0/8");

        acl.setRules(List.of(allow));
        acl.init(router.getDnsCache());

        assertFalse(acl.isPermitted("192.168.0.1"));
    }

    @Test
    void hostname_rule_triggers_dns_and_can_allow() {
        Allow allow = new Allow();
        allow.setTarget("^example\\.com$");

        acl.setRules(List.of(allow));
        when(dnsCache.getCanonicalHostName(any(InetAddress.class))).thenReturn("example.com");

        acl.init(router.getDnsCache());

        assertTrue(acl.isPermitted("1.2.3.4"));
        verify(dnsCache, times(1)).getCanonicalHostName(any(InetAddress.class));
    }

    @Test
    void hostname_rule_triggers_dns_and_can_deny_when_mismatch() {
        Allow allow = new Allow();
        allow.setTarget("^example\\.com$");

        acl.setRules(List.of(allow));
        when(dnsCache.getCanonicalHostName(any(InetAddress.class))).thenReturn("nope.example.org");

        acl.init(router.getDnsCache());

        assertFalse(acl.isPermitted("1.2.3.4"));
        verify(dnsCache, times(1)).getCanonicalHostName(any(InetAddress.class));
    }

    @Test
    void accessRule_setTarget_rejects_null_or_empty() {
        Allow allow = new Allow();
        assertThrows(ConfigurationException.class, () -> allow.setTarget(null));
        assertThrows(ConfigurationException.class, () -> allow.setTarget(""));
    }

    @Test
    void accessRule_setTarget_rejects_blank_only_spaces() {
        Allow allow = new Allow();
        assertThrows(ConfigurationException.class, () -> allow.setTarget("   "));
    }

    @Test
    void accessRule_setTarget_trims_value() {
        Allow allow = new Allow();
        allow.setTarget(" 10.0.0.0/8 ");
        assertEquals("10.0.0.0/8", allow.getTarget());
    }

    @Test
    void accessRule_setTarget_rejects_invalid_targets() {
        Allow allow = new Allow();
        assertThrows(ConfigurationException.class, () -> allow.setTarget(" 999.1.1.1/33 "));
        assertThrows(ConfigurationException.class, () -> allow.setTarget(" 2001:db8::1/129 "));
        assertThrows(ConfigurationException.class, () -> allow.setTarget(" [ "));
    }

    @Test
    void init_does_not_trigger_dns_when_no_hostname_rule_present() {
        Allow allow = new Allow();
        allow.setTarget("0.0.0.0/0");

        acl.setRules(List.of(allow));
        acl.init(router.getDnsCache());

        assertTrue(acl.isPermitted("1.2.3.4"));
        verify(dnsCache, never()).getCanonicalHostName(any(InetAddress.class));
    }

    @Test
    void init_triggers_dns_when_hostname_rule_present_even_if_other_rules_exist() {
        Allow allowIp = new Allow();
        allowIp.setTarget("10.0.0.0/8");

        Allow allowHost = new Allow();
        allowHost.setTarget("^example\\.com$");

        acl.setRules(List.of(allowIp, allowHost));
        when(dnsCache.getCanonicalHostName(any(InetAddress.class))).thenReturn("example.com");

        acl.init(router.getDnsCache());

        assertTrue(acl.isPermitted("10.1.2.3"));
        verify(dnsCache, times(1)).getCanonicalHostName(any(InetAddress.class));
    }

    @Test
    void isPermitted_with_leading_trailing_spaces_in_remoteIp_is_handled() {
        Allow allow = new Allow();
        allow.setTarget("10.0.0.0/8");

        acl.setRules(List.of(allow));
        acl.init(router.getDnsCache());

        assertTrue(acl.isPermitted(" 10.123.45.67 "));
        assertFalse(acl.isPermitted(" 11.0.0.1 "));
    }

    @Test
    void complex_ruleset_multiple_requests_first_decision_wins() {
        Deny denyBadHost = new Deny();
        denyBadHost.setTarget("^bad\\.example\\.com$");

        Allow allowInternal = new Allow();
        allowInternal.setTarget("10.1.0.0/16");

        Deny denyInternalSubnet = new Deny();
        denyInternalSubnet.setTarget("10.1.2.0/24");

        Allow allowSingle = new Allow();
        allowSingle.setTarget("203.0.113.7/32");

        acl.setRules(List.of(denyBadHost, allowInternal, denyInternalSubnet, allowSingle));

        when(dnsCache.getCanonicalHostName(any(InetAddress.class))).thenAnswer(inv -> {
            InetAddress ia = inv.getArgument(0);
            return switch (ia.getHostAddress()) {
                case "10.1.2.3" -> "bad.example.com";
                case "10.1.5.6", "198.51.100.10" -> "good.example.com";
                case "203.0.113.7" -> "any.example.com";
                default -> "unknown.example.com";
            };
        });

        acl.init(router.getDnsCache());

        assertFalse(acl.isPermitted("10.1.2.3"));
        assertTrue(acl.isPermitted("10.1.5.6"));
        assertTrue(acl.isPermitted("10.1.2.99"));
        assertTrue(acl.isPermitted("203.0.113.7"));
        assertFalse(acl.isPermitted("198.51.100.10"));
        assertFalse(acl.isPermitted("8.8.8.8"));
        verify(dnsCache, atLeast(6)).getCanonicalHostName(any(InetAddress.class));
    }

    @Test
    void localhost() {
        Allow allowLocalhost = new Allow();
        allowLocalhost.setTarget("127.0.0.1/32");

        acl.setRules(List.of(allowLocalhost));
        acl.init(router.getDnsCache());

        assertTrue(acl.isPermitted("127.0.0.1"));
        assertFalse(acl.isPermitted("127.0.0.2"));
    }

}
