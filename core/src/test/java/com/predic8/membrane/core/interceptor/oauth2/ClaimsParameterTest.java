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

import com.predic8.membrane.core.interceptor.oauth2.parameter.ClaimsParameter;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

public class ClaimsParameterTest {

    ClaimsParameter cp;

    private HashSet<String> createSupportedClaims(String... claims){
        HashSet<String> supportedClaims = new HashSet<>();
        for(String claim : claims)
            supportedClaims.add(claim);
        return supportedClaims;
    }

    @Test
    public void testValidParsing() throws Exception{
        cp = new ClaimsParameter(createSupportedClaims("email","sub"), OAuth2TestUtil.getMockClaims());
        assertEquals(OAuth2TestUtil.getMockClaims(),cp.toJson());
    }

    @Test
    public void testParsingNoIdToken() throws Exception{
        cp = new ClaimsParameter(createSupportedClaims("email"), OAuth2TestUtil.getMockClaims());
        assertEquals(getMockClaimsEmailInBoth(),cp.toJson());
    }

    @Test
    public void testParsingNoUserinfo() throws Exception{
        cp = new ClaimsParameter(createSupportedClaims("sub"), OAuth2TestUtil.getMockClaims());
        assertEquals(getMockClaimsIdToken(),cp.toJson());
    }

    static String getMockClaimsUserinfo() throws IOException {
        String[] userinfoClaims = {"email"};
        return ClaimsParameter.writeCompleteJson(userinfoClaims,null);
    }

    static String getMockClaimsIdToken() throws IOException {
        String[] idTokenClaims = {"sub"};
        return ClaimsParameter.writeCompleteJson(null,idTokenClaims);
    }

    static String getMockClaimsEmailInBoth() throws IOException {
        String[] userinfoClaims = {"email"};
        String[] idTokenClaims = {"email"};
        return ClaimsParameter.writeCompleteJson(userinfoClaims,idTokenClaims);
    }


}
