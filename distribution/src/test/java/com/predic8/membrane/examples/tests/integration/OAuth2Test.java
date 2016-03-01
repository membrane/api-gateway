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

package com.predic8.membrane.examples.tests.integration;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.test.AssertUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class OAuth2Test{


    private Router router;

    @Before
    public void setUp() throws Exception{
        router = HttpRouter.init(System.getProperty("user.dir")+ "\\src\\test\\resources\\OAuth2\\proxies.xml");
    }

    @Test
    public void testGoodRequest() throws Exception{
        AssertUtils.getAndAssert200("http://localhost:2001");
        String[] headers = new String[2];
        headers[0] = "Content-Type";
        headers[1] = "application/x-www-form-urlencoded";
        AssertUtils.postAndAssert(200,"http://localhost:2000/login/",headers,"target=&username=john&password=password");
        Assert.assertEquals("Hello john.", AssertUtils.getAndAssert200("http://localhost:2000/"));
    }

    @Test
    public void testBadUserCredentials() throws Exception{
        AssertUtils.getAndAssert200("http://localhost:2001");
        String[] headers = new String[2];
        headers[0] = "Content-Type";
        headers[1] = "application/x-www-form-urlencoded";
        Assert.assertEquals(true,AssertUtils.postAndAssert(200,"http://localhost:2000/login/",headers,"target=&username=john&password=wrongPassword").contains("Invalid password."));
        AssertUtils.getAndAssert(400,"http://localhost:2000/");
    }

    @Test
    public void testMissingHeader() throws Exception{
        AssertUtils.getAndAssert200("http://localhost:2001");
        Assert.assertEquals(true,AssertUtils.postAndAssert(200,"http://localhost:2000/login/","target=&username=john&password=password").contains("Invalid password."));
        AssertUtils.getAndAssert(400,"http://localhost:2000/");
    }

    @Test
    public void testBypassingAuthorizationService() throws Exception{
        AssertUtils.getAndAssert(400,"http://localhost:2000/oauth2/auth");
    }

    @After
    public void tearDown() throws Exception{
        router.stopAll();
    }
}
