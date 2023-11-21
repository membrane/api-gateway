package com.predic8.membrane.core.interceptor.acl.matchers.Cidr;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class IpRangeTest {

    @Test
    void getMask() {
        assertEquals(0xFFFFFF00, IpRange.getMask(24)); // for 255.255.255.0
        assertEquals(0xFFFF0000, IpRange.getMask(16)); // for 255.255.0.0
        assertEquals(0xFF000000, IpRange.getMask(8));  // for 255.0.0.0
        assertEquals(0x80000000, IpRange.getMask(1));  // for 128.0.0.0
        assertEquals(~0, IpRange.getMask(32));                  // for 255.255.255.255
        assertEquals(0, IpRange.getMask(0));           // for 0.0.0.0
    }

    @Test
    void getIntegerValue() {
        assertEquals(0x7F000001, IpRange.getIntegerValue(new byte[] {127, 0, 0, 1}));
        assertEquals(0xC0A80001, IpRange.getIntegerValue(new byte[] {(byte) 192, (byte) 168, 0, 1}));
        assertEquals(0x00000000, IpRange.getIntegerValue(new byte[] {0, 0, 0, 0}));
        assertEquals(0xFFFFFFFF, IpRange.getIntegerValue(new byte[] {(byte) 255, (byte) 255, (byte) 255, (byte) 255}));
        assertEquals(0x00007F00, IpRange.getIntegerValue(new byte[] {0, 0, 127, 0}));
    }
}
