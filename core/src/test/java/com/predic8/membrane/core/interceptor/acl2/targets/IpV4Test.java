package com.predic8.membrane.core.interceptor.acl2.targets;

import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;

import static com.predic8.membrane.core.interceptor.acl2.targets.IpV4.IPV4_PATTERN;
import static org.junit.jupiter.api.Assertions.*;

class IpV4Test {

    @Test
    void testConstructionPattern() {
        Matcher matcher = IPV4_PATTERN.matcher("192.168.0.1/24");
        matcher.matches();
        assertEquals("192.168.0.1", matcher.group("address"));
        assertEquals("24", matcher.group("cidr"));
    }
}