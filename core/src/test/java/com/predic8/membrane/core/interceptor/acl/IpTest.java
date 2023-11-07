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
package com.predic8.membrane.core.interceptor.acl;

import java.net.UnknownHostException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.predic8.membrane.core.Router;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class IpTest {

    static Router router;

    static Hostname h1;

    @BeforeAll
    public static void setUp() throws Exception {
        router = new Router();

        h1 = new Hostname(router);
        h1.setSchema("localhost");
    }

    @Test
    public void test_localhost_matches_localhost_pattern() throws UnknownHostException{
        check("localhost", true);
    }

    private void check(String address, boolean b) throws UnknownHostException {
        assertEquals(b, h1.matches(address, address));
    }
}
