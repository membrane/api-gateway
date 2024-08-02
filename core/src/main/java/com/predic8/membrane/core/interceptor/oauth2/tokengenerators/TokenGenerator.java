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

package com.predic8.membrane.core.interceptor.oauth2.tokengenerators;

import com.predic8.membrane.core.Router;

import java.util.NoSuchElementException;

public interface TokenGenerator {
    public void init(Router router) throws Exception;

    String getTokenType();
    String getToken(String username, String clientId, String clientSecret);
    String getUsername(String token) throws NoSuchElementException;
    String getClientId(String token) throws NoSuchElementException;
    void invalidateToken(String token, String clientId, String clientSecret)throws NoSuchElementException;
    boolean supportsRevocation();

    /**
     * @return token expiration in seconds, or 0 if there is no expiration
     */
    long getExpiration();
}
