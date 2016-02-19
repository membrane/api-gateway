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

import com.predic8.membrane.annot.MCElement;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.NoSuchElementException;

@MCElement(name="bearerToken")
public class BearerTokenGenerator implements TokenGenerator {

    private SecureRandom random = new SecureRandom();
    private HashMap<String,String> tokenToUsername = new HashMap<String, String>();

    @Override
    public String getTokenType() {
        return "bearer";
    }

    @Override
    public String getToken(String username, String clientId) {
        String token = new BigInteger(130, random).toString(32);
        tokenToUsername.put(token,username);
        return token;
    }

    @Override
    public String getUsername(String token) throws NoSuchElementException {
        try {
            return tokenToUsername.get(token);
        }catch(Exception e){
            throw new NoSuchElementException(e.getMessage());
        }
    }

    @Override
    public void invalidateToken(String token) {
        tokenToUsername.remove(token);
    }
}
