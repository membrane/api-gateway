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

import com.fasterxml.jackson.core.JsonGenerator;
import com.predic8.membrane.core.interceptor.oauth2.parameter.ClaimsParameter;
import org.junit.Test;

import java.io.IOException;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;

public class ClaimsParameterTest {

    ClaimsParameter cp;

    private HashSet<String> createSupportedClaims(String... claims){
        HashSet<String> supportedClaims = new HashSet<String>();
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
        assertEquals(getMockClaimsUserinfo(),cp.toJson());
    }

    @Test
    public void testParsingNoUserinfo() throws Exception{
        cp = new ClaimsParameter(createSupportedClaims("sub"), OAuth2TestUtil.getMockClaims());
        assertEquals(getMockClaimsIdToken(),cp.toJson());
    }

    private static String writeSingleObjectJson(String objName, String... claims) throws IOException {
        ReusableJsonGenerator jsonGen = new ReusableJsonGenerator();
        JsonGenerator gen = jsonGen.resetAndGet();
        gen.writeStartObject();
        writeMockClaimsSingleObject(gen,objName,claims);
        gen.writeEndObject();
        return jsonGen.getJson();
    }

    static String getMockClaimsUserinfo() throws IOException {
        return writeSingleObjectJson("userinfo","email");
    }

    static String getMockClaimsIdToken() throws IOException {
        return writeSingleObjectJson("id_token","sub");
    }

    static void writeMockClaimsSingleObject(JsonGenerator gen, String objectName,String... claims) throws IOException {
        gen.writeObjectFieldStart(objectName);
        for(String claim : claims)
            gen.writeObjectField(claim,null);
        gen.writeEndObject();
    }


}
