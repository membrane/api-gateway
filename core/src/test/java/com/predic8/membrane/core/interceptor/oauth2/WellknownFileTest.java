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

import com.predic8.membrane.core.HttpRouter;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class WellknownFileTest {

    final String authServerUrl = "http://testserver.com/oauth2/";

    @Test
    public void testValidWellknownFile() throws Exception{
        WellknownFile wkf = new WellknownFile();

        wkf.setIssuer("http://testissuer.com");
        wkf.setAuthorizationEndpoint(authServerUrl + "auth");
        wkf.setTokenEndpoint(authServerUrl + "token");
        wkf.setUserinfoEndpoint(authServerUrl + "userinfo");
        wkf.setRevocationEndpoint(authServerUrl + "revoke");
        wkf.setJwksUri(authServerUrl + "certs");
        wkf.setSupportedResponseTypes("code token");
        wkf.setSupportedSubjectType("public");
        wkf.setSupportedIdTokenSigningAlgValues("RS256");
        wkf.setSupportedScopes("openid email profile");
        wkf.setSupportedTokenEndpointAuthMethods("client_secret_post");
        wkf.setSupportedClaims("sub email username");

        wkf.init(new HttpRouter());

        assertEquals(expectedWellknownFile(),wkf.getWellknown());
    }

    private String expectedWellknownFile(){
        return "{\"issuer\":\"http://testissuer.com\",\"authorization_endpoint\":\"http://testserver.com/oauth2/auth\",\"token_endpoint\":\"http://testserver.com/oauth2/token\",\"userinfo_endpoint\":\"http://testserver.com/oauth2/userinfo\",\"revocation_endpoint\":\"http://testserver.com/oauth2/revoke\",\"jwks_uri\":\"http://testserver.com/oauth2/certs\",\"response_types_supported\":[\"code\",\"token\"],\"subject_types_supported\":[\"public\"],\"id_token_signing_alg_values_supported\":[\"RS256\"],\"scopes_supported\":[\"openid\",\"email\",\"profile\"],\"token_endpoint_auth_methods_supported\":[\"client_secret_post\"],\"claims_supported\":[\"sub\",\"email\",\"username\"]}";
    }
}
