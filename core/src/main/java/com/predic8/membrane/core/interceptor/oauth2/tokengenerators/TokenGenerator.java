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

import java.util.Map;
import java.util.NoSuchElementException;

public interface TokenGenerator {
    public void init(Router router) throws Exception;

    /**
     * @return the token type used, probably "Bearer".
     */
    String getTokenType();

    /**
     * @return a new token for the specified user and client.
     */
    String getToken(String username, String clientId, String clientSecret, Map<String, Object> additionalClaims);

    /**
     * Checks the token for validity. Returns the username the token was generated for.
     * @param token The token.
     * @return The username.
     * @throws NoSuchElementException if the token is not valid or no username is contained in the token.
     */
    String getUsername(String token) throws NoSuchElementException;

    /**
     * Checks the token for validity. Returns the additional claims the token was generated for.
     * @param token The token.
     * @return The additional claims.
     * @throws NoSuchElementException if the token is not valid.
     */
    Map<String, Object> getAdditionalClaims(String token) throws NoSuchElementException;

    /**
     * Checks the token for validity. Returns the clientId the token was generated for.
     * @param token The token.
     * @return The clientId.
     * @throws NoSuchElementException if the token is not valid or no clientId is contained in the token.
     */
    String getClientId(String token) throws NoSuchElementException;

    /**
     * Revokes a token.
     */
    void invalidateToken(String token, String clientId, String clientSecret)throws NoSuchElementException;

    /**
     * @return whether this token manager supports revocation (also known as "invalidation")
     */
    boolean supportsRevocation();

    /**
     * @return token expiration in seconds, or 0 if there is no expiration
     */
    long getExpiration();

    /**
     * @return the JWK representation of the key used to sign the tokens or null, if there is no such key (e.g. because
     * the tokens are randomly generated strings)
     */
    String getJwkIfAvailable();
}