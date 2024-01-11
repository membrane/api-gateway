/* Copyright 2023 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
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
