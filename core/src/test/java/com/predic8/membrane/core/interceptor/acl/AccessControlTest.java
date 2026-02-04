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

import com.predic8.membrane.core.interceptor.acl.rules.*;
import com.predic8.membrane.core.router.*;
import com.predic8.membrane.core.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import java.net.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
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
        acl.setRules(List.of(allow("0.0.0.0/0")));
        acl.init(router.getDnsCache());

        assertFalse(acl.isPermitted(null).permitted());
        assertFalse(acl.isPermitted("").permitted());
        assertFalse(acl.isPermitted("   ").permitted());
        assertFalse(acl.isPermitted("not-an-ip").permitted());
    }

    @Test
    void isPermitted_allows_when_matching_allow_rule() {
        acl.setRules(List.of(allow("10.0.0.0/8")));
        acl.init(router.getDnsCache());

        assertTrue(acl.isPermitted("10.123.45.67").permitted());
        assertFalse(acl.isPermitted("11.0.0.1").permitted());
    }

    @Test
    void isPermitted_denies_when_matching_deny_rule() {
        acl.setRules(List.of(deny("10.0.0.0/8")));
        acl.init(router.getDnsCache());
        assertFalse(acl.isPermitted("10.123.45.67").permitted());
        assertFalse(acl.isPermitted("11.0.0.1").permitted());
    }

    @Test
    void isPermitted_first_decision_wins() {
        acl.setRules(List.of(deny("10.0.0.0/8"), allow("10.0.0.0/8")));
        acl.init(router.getDnsCache());
        assertFalse(acl.isPermitted("10.1.2.3").permitted());
    }

    @Test
    void isPermitted_no_rule_matches_defaults_to_deny() {
        acl.setRules(List.of(allow("10.0.0.0/8")));
        acl.init(router.getDnsCache());
        assertFalse(acl.isPermitted("192.168.0.1").permitted());
    }

    @Test
    void hostname_rule_triggers_dns_and_can_allow() {
        acl.setRules(List.of(allow("^example\\.com$")));
        when(dnsCache.getCanonicalHostName(any(InetAddress.class))).thenReturn("example.com");

        acl.init(router.getDnsCache());

        assertTrue(acl.isPermitted("1.2.3.4").permitted());
        verify(dnsCache, times(1)).getCanonicalHostName(any(InetAddress.class));
    }

    @Test
    void hostname_rule_triggers_dns_and_can_deny_when_mismatch() {
        acl.setRules(List.of(allow("^example\\.com$")));
        when(dnsCache.getCanonicalHostName(any(InetAddress.class))).thenReturn("nope.example.org");

        acl.init(router.getDnsCache());

        assertFalse(acl.isPermitted("1.2.3.4").permitted());
        verify(dnsCache, times(1)).getCanonicalHostName(any(InetAddress.class));
    }

    @Test
    void accessRule_setTarget_rejects_null_or_empty() {
        assertThrows(ConfigurationException.class, () -> allow("0.0.0.0/0").setTarget(null));
        assertThrows(ConfigurationException.class, () -> allow("0.0.0.0/0").setTarget(""));
    }

    @Test
    void accessRule_setTarget_rejects_blank_only_spaces() {
        assertThrows(ConfigurationException.class, () -> allow("0.0.0.0/0").setTarget("   "));
    }

    @Test
    void accessRule_setTarget_trims_value() {
        assertEquals("10.0.0.0/8", allow("10.0.0.0/8").getTarget());
    }

    @Test
    void accessRule_setTarget_rejects_invalid_targets() {
        assertThrows(ConfigurationException.class, () -> allow(" 999.1.1.1/33 "));
        assertThrows(ConfigurationException.class, () -> allow(" 2001:db8::1/129 "));
        assertThrows(ConfigurationException.class, () -> allow(" [ "));
    }

    @Test
    void init_does_not_trigger_dns_when_no_hostname_rule_present() {
        acl.setRules(List.of(allow("0.0.0.0/0")));
        acl.init(router.getDnsCache());

        assertTrue(acl.isPermitted("1.2.3.4").permitted());
        verify(dnsCache, never()).getCanonicalHostName(any(InetAddress.class));
    }

    @Test
    void init_triggers_dns_when_hostname_rule_present_even_if_other_rules_exist() {
        acl.setRules(List.of(allow("10.0.0.0/8"), allow("^example\\.com$")));
        when(dnsCache.getCanonicalHostName(any(InetAddress.class))).thenReturn("example.com");

        acl.init(router.getDnsCache());

        assertTrue(acl.isPermitted("10.1.2.3").permitted());
        verify(dnsCache, times(1)).getCanonicalHostName(any(InetAddress.class));
    }

    @Test
    void isPermitted_with_leading_trailing_spaces_in_remoteIp_is_handled() {
        acl.setRules(List.of(allow("10.0.0.0/8")));
        acl.init(router.getDnsCache());

        assertTrue(acl.isPermitted(" 10.123.45.67 ").permitted());
        assertFalse(acl.isPermitted(" 11.0.0.1 ").permitted());
    }

    @Test
    void complex_ruleset_multiple_requests_first_decision_wins() {
        acl.setRules(List.of(deny("^bad\\.example\\.com$"), allow("10.1.0.0/16"), deny("10.1.2.0/24"), allow("203.0.113.7/32")));

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

        assertFalse(acl.isPermitted("10.1.2.3").permitted());
        assertTrue(acl.isPermitted("10.1.5.6").permitted());
        assertTrue(acl.isPermitted("10.1.2.99").permitted());
        assertTrue(acl.isPermitted("203.0.113.7").permitted());
        assertFalse(acl.isPermitted("198.51.100.10").permitted());
        assertFalse(acl.isPermitted("8.8.8.8").permitted());
        verify(dnsCache, atLeast(6)).getCanonicalHostName(any(InetAddress.class));
    }

    @Test
    void localhost() {
        acl.setRules(List.of(allow("127.0.0.1/32")));
        acl.init(router.getDnsCache());

        assertTrue(acl.isPermitted("127.0.0.1").permitted());
        assertFalse(acl.isPermitted("127.0.0.2").permitted());
    }

    @ParameterizedTest
    @CsvSource({
            "0.0.0.0/0, 192.168.2.1",
            "::/0, 2001:0DB8:0:CD30::1"
    })
    void any_cidr_matches_all_addresses(String cidr, String ipAddress) {
        var ad = allow(cidr).apply(IpAddress.parse(ipAddress));
        assertTrue(ad.isPresent());
        assertTrue(ad.get().permitted());
    }

    private static Allow allow(String target) {
        var allow = new Allow();
        allow.setTarget(target);
        return allow;
    }

    private static Deny deny(String target) {
        var deny = new Deny();
        deny.setTarget(target);
        return deny;
    }
}
