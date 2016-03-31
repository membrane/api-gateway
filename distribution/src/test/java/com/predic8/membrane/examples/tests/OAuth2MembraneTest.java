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
import com.predic8.membrane.test.AssertUtils;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class OAuth2MembraneTest extends DistributionExtractingTestcase {

    @Test
    public void test() throws Exception {
        Process2 sl = new Process2.Builder().in(getExampleDir("oauth2/membrane/authorization_server")).script("service-proxy").waitForMembrane()
                .start();

        Process2 sl2 = new Process2.Builder().in(getExampleDir("oauth2/membrane/client")).script("service-proxy").waitForMembrane()
                .start();
        try {
            AssertUtils.getAndAssert200("http://localhost:2001");
            String[] headers = new String[2];
            headers[0] = "Content-Type";
            headers[1] = "application/x-www-form-urlencoded";
            AssertUtils.postAndAssert(200,"http://localhost:2000/login/",headers,"target=&username=john&password=password");
            AssertUtils.postAndAssert(200,"http://localhost:2000/login/consent",headers,"target=&consent=Accept");
            Assert.assertEquals(AssertUtils.getAndAssert200("http://thomas-bayer.com"), AssertUtils.getAndAssert200("http://localhost:2000/"));
        } finally {
            sl.killScript();
            sl2.killScript();
        }
    }
}
