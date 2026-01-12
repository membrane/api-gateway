package com.predic8.membrane.core.interceptor.acl2.targets;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IpV4Test {

    @Test
    void acceptsIpWithCidr() {
        assertTrue(IpV4.accepts("192.168.0.1/24"));
    }

    @Test
    void acceptsIpWithoutCidr() {
        assertTrue(IpV4.accepts("192.168.0.1"));
    }

    @Test
    void deniesIpWithInvalidCidr() {
        assertFalse(IpV4.accepts("192.168.0.1/33"));
        assertFalse(IpV4.accepts("192.168.0.1/128"));
    }

    @Test
    void deniesInvalidIp() {
        assertTrue(IpV4.accepts("192.168.0.321"));
        assertTrue(IpV4.accepts("192.168.0.0.1"));
    }
}