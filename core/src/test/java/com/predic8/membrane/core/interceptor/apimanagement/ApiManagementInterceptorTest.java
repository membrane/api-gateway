/*
 * Copyright 2015 predic8 GmbH, www.predic8.com
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

package com.predic8.membrane.core.interceptor.apimanagement;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.rules.ServiceProxy;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
//import java.nio.file.Files;
//import java.nio.file.Paths;

public class ApiManagementInterceptorTest {

    Exchange exc;
    ApiManagementInterceptor ami;

    @Test
    public void testApiManagementInterceptorValidRequest() throws Exception {
        /*String service = "Order API";
        String headerName = "Authorization";
        String apiKey = "abcdefghi";
        Outcome expectedOutcome = Outcome.CONTINUE;

        Exchange exc = new Exchange(null);
        exc.setRule(new ServiceProxy());
        exc.getRule().setName(service);

        Header hd = new Header();
        hd.add(headerName,apiKey);
        //hd.removeFields(headerName);
        exc.setRequest(new Request.Builder().get("").header(hd).build());

        String source =  new String(Files.readAllBytes(Paths.get(System.getProperty("user.dir") + "\\src\\test\\resources\\apimanagement\\api.yaml")), Charset.defaultCharset());
        InputStream in = IOUtils.toInputStream(source, Charset.defaultCharset());

        ApiManagementInterceptor ami = new ApiManagementInterceptor();

        Assert.assertEquals(expectedOutcome,ami.handleRequest(exc));*/
    }
}
