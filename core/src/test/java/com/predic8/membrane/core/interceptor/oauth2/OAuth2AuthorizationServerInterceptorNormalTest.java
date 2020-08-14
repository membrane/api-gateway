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

package com.predic8.membrane.core.interceptor.oauth2;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.util.Util;
import com.predic8.membrane.core.util.functionalInterfaces.Consumer;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import javax.mail.internet.ParseException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.concurrent.Callable;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class OAuth2AuthorizationServerInterceptorNormalTest extends OAuth2AuthorizationServerInterceptorBase {

    @Before
    public void setUp() throws Exception{
        super.setUp();
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() throws Exception {
        return Arrays.asList(new Object[][] {
                testBadRequest(),
                testGoodAuthRequest(),
                testGoodGrantedAuthCode(),
                testGoodTokenRequest(),
                testGoodUserinfoRequest(),
                testGoodRevocationRequest()
        });
    }

    private static Object[] testGoodRevocationRequest() throws Exception{
        return new Object[]{"testGoodRevocationRequest", runUntilGoodTokenRequest(),getMockRevocationRequest(),200,noPostprocessing()};
    }

    private static Object[] testGoodUserinfoRequest() throws Exception{
        return new Object[]{"testGoodUserinfoRequest", runUntilGoodTokenRequest(),getMockUserinfoRequest(),200,userinfoRequestPostprocessing()};
    }

    private static Object[] testGoodTokenRequest() throws Exception{
        return new Object[]{"testGoodTokenRequest", runUntilGoodGrantedAuthCode(),getMockTokenRequest(),200, getTokenAndTokenTypeFromResponse()};
    }

    private static Object[] testGoodGrantedAuthCode() throws Exception {
        return new Object[]{"testGoodGrantedAuthCode", runUntilGoodAuthRequest(), getMockEmptyEndpointRequest(), 307, getCodeFromResponse()};
    }

    private static Object[] testGoodAuthRequest() throws Exception {
        return new Object[]{"testGoodAuthRequest", noPreprocessing(), getMockAuthRequestExchange(),307, loginAsJohn()};
    }

    private static Object[] testBadRequest() throws Exception {
        return new Object[]{"testBadRequest", noPreprocessing(), getMockBadRequestExchange(),400, noPostprocessing()};
    }

    public static Callable<Exchange> getMockBadRequestExchange() throws Exception {
        return new Callable<Exchange>() {
            @Override
            public Exchange call() throws Exception {
                return new Request.Builder().get("/thisdoesntexist").buildExchange();
            }
        };
    }

    private static Consumer<Exchange> userinfoRequestPostprocessing() throws IOException, ParseException {
        return new Consumer<Exchange>() {
            @Override
            public void call(Exchange exchange) throws Exception {
                HashMap<String, String> json = Util.parseSimpleJSONResponse(exc.getResponse());
                assertEquals("john",json.get("username"));
            }
        };
    }

    public static Callable<Exchange> getMockRevocationRequest() throws Exception {
        return new Callable<Exchange>() {
            @Override
            public Exchange call() throws Exception {
                return new Request.Builder().post(mas.getRevocationEndpoint())
                        .header(Header.CONTENT_TYPE, "application/x-www-form-urlencoded")
                        .header(Header.USER_AGENT, Constants.USERAGENT)
                        .body("token=" + afterTokenGenerationToken +"&client_id=" + mas.getClientId() + "&client_secret=" + mas.getClientSecret())
                        .buildExchange();
            }
        };
    }

    public static Callable<Exchange> getMockPasswordRequestExchange() throws Exception {
        return new Callable<Exchange>() {
            @Override
            public Exchange call() throws Exception {
                return new Request.Builder().post(mas.getTokenEndpoint())
                        .header(Header.CONTENT_TYPE, "application/x-www-form-urlencoded")
                        .body("grant_type=password&username=john&password=password&client_id=abc&client_secret=def")
                        .buildExchange();
            }
        };
    }

    public static Callable<Exchange> getMockClientCredentialsRequestExchange() throws Exception {
        return new Callable<Exchange>() {
            @Override
            public Exchange call() throws Exception {
                return new Request.Builder().post(mas.getTokenEndpoint())
                        .header(Header.CONTENT_TYPE, "application/x-www-form-urlencoded")
                        .body("grant_type=password&client_id=abc&client_secret=def")
                        .buildExchange();
            }
        };
    }
}
