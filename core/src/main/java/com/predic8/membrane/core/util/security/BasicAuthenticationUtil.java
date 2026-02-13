/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.util.security;

import com.predic8.membrane.core.exchange.*;

import java.util.*;

import static java.nio.charset.StandardCharsets.*;

public class BasicAuthenticationUtil {

    private static final String BASIC_PREFIX = "Basic ";

    /**
     * Basic authentication credentials extracted from Authorization header.
     *
     * @param username The username (may be empty)
     * @param password The password (may be empty)
     */
    public record BasicCredentials(String username, String password) {
        public BasicCredentials {
            Objects.requireNonNull(username, "Username cannot be null");
            Objects.requireNonNull(password, "Password cannot be null");
        }

        /**
         * Converts credentials to a Map for compatibility with UserDataProvider.verify()
         */
        public Map<String, String> toMap() {
            Map<String, String> map = new HashMap<>();
            map.put("username", username);
            map.put("password", password);
            return map;
        }

        public String toString() {
            return "BasicCredentials{username='" + username + "', password='*******'}";
        }
    }

    /**
     * Extracts and decodes Basic authentication credentials from the Authorization header.
     * <p>
     * The "Basic" authentication scheme defined in RFC 7617 specifies that credentials
     * should be Base64-encoded in the format "username:password". RFC 7617 clarifies
     * that UTF-8 should be used for encoding.
     * </p>
     *
     * @param exc The exchange containing the Authorization header
     * @return BasicCredentials record with username and password
     * @throws IllegalArgumentException if the header is missing, invalid, not Basic auth,
     *         has invalid Base64, or is missing the colon separator
     */
    public static BasicCredentials getCredentials(Exchange exc) {
        return parseCredentials(decodeAuthorizationHeader(exc));
    }

    /**
     * Decodes the Authorization header and returns the raw credentials string.
     *
     * @param exc The exchange
     * @return The decoded credentials string in format "username:password"
     * @throws IllegalArgumentException if the header is missing or invalid
     */
    private static String decodeAuthorizationHeader(Exchange exc) {
        var header = exc.getRequest().getHeader().getAuthorization();

        if (header == null || header.isEmpty()) {
            throw new IllegalArgumentException("Authorization header is required");
        }

        // Check for Basic authentication scheme (case-insensitive per RFC 7617)
        if (!header.regionMatches(true, 0, BASIC_PREFIX, 0, BASIC_PREFIX.length())) {
            throw new IllegalArgumentException("Not a Basic authentication header");
        }

        // Check if there's actually content after "Basic "
        if (header.length() <= BASIC_PREFIX.length()) {
            throw new IllegalArgumentException("Missing credentials in Basic authentication header");
        }

        String base64Credentials = header.substring(BASIC_PREFIX.length()).trim();

        if (base64Credentials.isEmpty()) {
            throw new IllegalArgumentException("Empty credentials in Basic authentication header");
        }

        // Decode Base64
        try {
            return new String(Base64.getDecoder().decode(base64Credentials), UTF_8);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid Base64 encoding in Basic authentication header", e);
        }
    }

    /**
     * Parses decoded credentials string into username and password.
     * Splits on the first colon to support passwords containing colons.
     *
     * @param credentials The decoded credentials string (e.g., "user:pass")
     * @return BasicCredentials record
     * @throws IllegalArgumentException if no colon separator is found
     */
    private static BasicCredentials parseCredentials(String credentials) {
        int colonIndex = credentials.indexOf(':');

        if (colonIndex == -1) {
            throw new IllegalArgumentException("Invalid credentials format: missing colon separator");
        }

        String username = credentials.substring(0, colonIndex);
        String password = credentials.substring(colonIndex + 1);

        return new BasicCredentials(username, password);
    }
}