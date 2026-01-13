package com.predic8.membrane.core.interceptor.acl2.targets;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IpV4Test {

    @ParameterizedTest(name = "accepts: \"{0}\"")
    @ValueSource(strings = {
            "192.168.0.1",
            "0.0.0.0",
            "255.255.255.255",
            "127.0.0.1",
            "10.0.0.1",
            "172.16.0.1",
            "192.168.0.1/0",
            "192.168.0.1/24",
            "192.168.0.1/32",
            "10.0.0.0/8",
            "172.16.0.0/12",
            "192.168.0.0/24"
    })
    void acceptsValid(String input) {
        assertTrue(IpV4.accepts(input));
    }

    @ParameterizedTest(name = "denies: \"{0}\"")
    @ValueSource(strings = {
            "192.168.0.1/33",
            "192.168.0.1/128",
            "192.168.0.1/-1",
            "192.168.0.1/",
            "192.168.0.1//24",
            "192.168.0.1/ 24",
            "192.168.0.321",
            "256.0.0.1",
            "-1.0.0.1",
            "192.168.0",
            "192.168.0.0.1",
            "192.168..1",
            "192.168.0.1.",
            ".192.168.0.1",
            "192.168.0.one",
            "192.168.0.1/abc",
            "abc",
            " 192.168.0.1",
            "192.168.0.1 "
    })
    void deniesInvalid(String input) {
        assertFalse(IpV4.accepts(input));
    }
}