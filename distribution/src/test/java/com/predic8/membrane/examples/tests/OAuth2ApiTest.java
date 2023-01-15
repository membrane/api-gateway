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

import com.predic8.membrane.examples.util.Process2;
import com.predic8.membrane.examples.util.BufferLogger;
import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.*;

public class OAuth2ApiTest extends DistributionExtractingTestcase {

    @Override
    protected String getExampleDirName() {
        return "oauth2/api";
    }

    @Test
    public void test() throws Exception {
        try (Process2 sl = new Process2.Builder().in(getExampleDir("oauth2/api/authorization_server")).script("service-proxy").waitForMembrane().start()) {

            Process2 sl2 = new Process2.Builder().in(getExampleDir("oauth2/api/token_validator")).script("service-proxy").waitForMembrane().start();

            BufferLogger logger = new BufferLogger();
            Process2 sl3 = new Process2.Builder().in(getExampleDir("oauth2/api")).withWatcher(logger).script("client").parameters("john password").waitAfterStartFor("200 O").start();
            // sl3 can fail because at least the start.sh is very fragile in parsing the response for the access token. If the number or order of the params changes then client.sh will fail.
            try {
                //This is kind of redundant as sl3 already waits until "OK" is written or timeouts when its not
                assertTrue(logger.toString().contains("200 O"));
            } finally {
                sl2.killScript();
                sl3.killScript();
            }
        }
    }
}
