/*
 * Copyright 2016 predic8 GmbH, www.predic8.com
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.examples.tests;

import com.predic8.membrane.examples.DistributionExtractingTestcase;
import com.predic8.membrane.examples.Process2;
import com.predic8.membrane.examples.util.BufferLogger;
import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.*;

public class OAuth2ApiTest extends DistributionExtractingTestcase {

    @Test
    public void test() throws Exception {
        Process2 sl = new Process2.Builder().in(getExampleDir("oauth2/api/authorization_server")).script("service-proxy").waitForMembrane()
                .start();

        Process2 sl2 = new Process2.Builder().in(getExampleDir("oauth2/api/token_validator")).script("service-proxy").waitForMembrane()
                .start();

        BufferLogger b = new BufferLogger();
        Process2 sl3 = new Process2.Builder().in(getExampleDir("oauth2/api")).withWatcher(b).script("start").waitAfterStartFor("OK").start();
        // sl3 can fail because at least the start.sh is very fragile in parsing the response for the access token. If the number or order of the params changes then start.sh will fail.
        try {
            //This is kind of redundant as sl3 already waits until "OK" is written or timeouts when its not
            assertTrue(b.toString().contains("OK"));
        } finally {
            sl.killScript();
            sl2.killScript();
            sl3.killScript();
        }
    }
}
