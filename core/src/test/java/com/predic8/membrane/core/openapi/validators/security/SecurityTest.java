/*
 *  Copyright 2022 predic8 GmbH, www.predic8.com
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.openapi.validators.security;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.openapi.model.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.openapi.validators.*;
import com.predic8.membrane.core.util.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static com.predic8.membrane.core.http.Request.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.openapi.util.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;


public class SecurityTest extends AbstractValidatorTest {

    private OpenAPIInterceptor interceptor;
    private Router router;

    @Override
    protected String getOpenAPIFileName() {
        return "/openapi/specs/security/security.yml";
    }

    @BeforeEach
    void setUpSpec() throws Exception {
        router = new Router();
        router.setUriFactory(new URIFactory());

        OpenAPISpec spec = new OpenAPISpec();
        spec.location = "src/test/resources/openapi/specs/security/security.yml";
        spec.validateRequests = OpenAPISpec.YesNoOpenAPIOption.YES;

        interceptor = new OpenAPIInterceptor(createProxy(router, spec));
        interceptor.init(router);
    }

    @Test
    void simpleHasScopes() {
        ValidationErrors errors = validator.validate(Request.get().path("/v1/finance").scopes("finance","read"));
        System.out.println("errors = " + errors);
        assertEquals(0,errors.size());
    }

    @Test
    void simpleMissingAllScopes() {
        ValidationErrors errors = validator.validate(Request.get().path("/v1/finance-and-write"));
        System.out.println("errors = " + errors);
        assertEquals(3,errors.size());
    }

    @Test
    void simpleMissingTwoScopes() {
        ValidationErrors errors = validator.validate(Request.get().path("/v1/finance-and-write").scopes("read"));
        System.out.println("errors = " + errors);
        assertEquals(2,errors.size());
    }

    @Test
    void simpleIgnoreAdditionalScopes() {
        ValidationErrors errors = validator.validate(Request.get().path("/v1/finance").scopes("read", "finance", "development"));
        System.out.println("errors = " + errors);
        assertEquals(0,errors.size());
    }

    @Test
    void fromInterceptorOnlyOAuth2() throws Exception {
        Exchange exc = get("/v1/finance").buildExchange();
        exc.setOriginalRequestUri("/v1/finance");

        Map<String,Object> jwt = new HashMap<>();
        jwt.put("scp","read write finance");

        exc.setProperty("jwt",jwt);

        Outcome outcome = interceptor.handleRequest(exc);
        assertEquals(CONTINUE, outcome);
    }

    @Test
    void fromInterceptorEmptyScopes() throws Exception {
        Exchange exc = get("/v1/finance").buildExchange();
        exc.setOriginalRequestUri("/v1/finance");

        Map<String,Object> jwt = new HashMap<>();
        exc.setProperty("jwt",jwt);

        Outcome outcome = interceptor.handleRequest(exc);
        assertEquals(RETURN, outcome);
        System.out.println("exc = " + exc.getResponse().getStatusCode());
        System.out.println("exc = " + exc.getResponse().getBodyAsStringDecoded());
    }

    // Scopes should not be mixed up when using multiple methods simultaneously.
//    @Test
//    void fromInterceptorOAuth2EndpointApiKeyScopeInjection() throws Exception {
//        Exchange exc = get("/v1/finance").buildExchange();
//        exc.setOriginalRequestUri("/v1/finance");
//
//        // Finance required but missing from OAuth2 scopes
//        Map<String,Object> jwt = new HashMap<>();
//        jwt.put("scp","read write");
//        exc.setProperty("jwt",jwt);
//
//        // Scope injected through API Key
//        exc.setProperty(SCOPES, List.of("finance"));
//
//        Outcome outcome = interceptor.handleRequest(exc);
//        // TODO fix the injection attack mechanism
//        assertEquals(RETURN, outcome);
//    }

//    @Test
//    void exclusiveApiKeyAuthentication() throws Exception {
//        Exchange exc = get("/v1/exclusive-apikey").buildExchange();
//        exc.setOriginalRequestUri("/v1/exclusive-apikey");
//
//        exc.setProperty(SCOPES, List.of("api_admin"));
//
//        // Currently only works with this, it expects read even though it's a different schema
//        // Additionally it expects it as AND even though it's an OR relation
//        //exc.setProperty(SCOPES, List.of("read", "api_admin", "api_user"));
//
//        Outcome outcome = interceptor.handleRequest(exc);
//        assertEquals(CONTINUE, outcome);
//    }

//    @Test
//    void oAuth2AndApiKeySecurity() throws Exception {
//        Exchange exc = get("/v1/oauth2-and-apikey").buildExchange();
//        exc.setOriginalRequestUri("/v1/oauth2-and-apikey");
//
//        Map<String, Object> jwt = new HashMap<>();
//        jwt.put("scp", List.of("read", "oauth_user"));
//        exc.setProperty("jwt", jwt);
//
//        exc.setProperty(SCOPES, List.of("api_user"));
//
//        Outcome outcome = interceptor.handleRequest(exc);
//        assertEquals(CONTINUE, outcome);
//    }
//
//    @Test
//    void oauth2OrApiKeySecurity_OAuth2() throws Exception {
//        Exchange exc = get("/v1/oauth2-or-apikey").buildExchange();
//        exc.setOriginalRequestUri("/v1/oauth2-or-apikey");
//
//        Map<String, Object> jwt = new HashMap<>();
//        jwt.put("scp", List.of("read", "finance"));
//        exc.setProperty("jwt", jwt);
//
//        Outcome outcome = interceptor.handleRequest(exc);
//        assertEquals(CONTINUE, outcome);
//    }
//
//    @Test
//    void oauth2OrApiKeySecurity_ApiKey() throws Exception {
//        Exchange exc = get("/v1/oauth2-or-apikey").buildExchange();
//        exc.setOriginalRequestUri("/v1/oauth2-or-apikey");
//
//        exc.setProperty(SCOPES, List.of("admin"));
//
//        Outcome outcome = interceptor.handleRequest(exc);
//        assertEquals(CONTINUE, outcome);
//    }
//
//    // APIKeys sollten Schemaweise betrachtet werden, scopes von key1 sollten nicht durch key2 akzeptiert werden usw.
//
//    @Test
//    void multipleApiKeyAnd() throws Exception {
//        Exchange exc = get("/v1/apikey-and-apikey").buildExchange();
//        exc.setOriginalRequestUri("/v1/apikey-and-apikey");
//
//        exc.setProperty(SCOPES, List.of("api_access", "admin_access"));
//
//        Outcome outcome = interceptor.handleRequest(exc);
//        assertEquals(CONTINUE, outcome);
//    }
//
//    @Test
//    void multipleApiKeyOrLogic_FirstKey() throws Exception {
//        Exchange excFirst = get("/v1/apikey-or-apikey").buildExchange();
//        excFirst.setOriginalRequestUri("/v1/apikey-or-apikey");
//
//        excFirst.setProperty("SCOPES", List.of("first_key_scope"));
//
//        Outcome outcomeFirst = interceptor.handleRequest(excFirst);
//        assertEquals(CONTINUE, outcomeFirst);
//    }
//
//    @Test
//    void multipleApiKeyOrLogic_SecondKey() throws Exception {
//        Exchange excSecond = get("/v1/apikey-or-apikey").buildExchange();
//        excSecond.setOriginalRequestUri("/v1/apikey-or-apikey");
//
//        excSecond.setProperty("SCOPES", List.of("second_key_scope"));
//
//        Outcome outcomeSecond = interceptor.handleRequest(excSecond);
//        assertEquals(CONTINUE, outcomeSecond);
//    }
}