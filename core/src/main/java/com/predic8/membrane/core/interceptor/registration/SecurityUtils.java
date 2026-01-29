/* Copyright 2017 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.registration;

import com.google.common.io.BaseEncoding;
import org.apache.commons.codec.digest.Crypt;
import org.bouncycastle.crypto.generators.OpenBSDBCrypt;

import java.security.SecureRandom;
import java.util.regex.Pattern;

public class SecurityUtils {

    private static final SecureRandom secureRandom = new SecureRandom();
    public static final Pattern HEX_PASSWORD_PATTERN = Pattern.compile("\\$([^$]+)\\$([^$]+)\\$.+");
    private static final Pattern BCRYPT_ID_PATTERN = Pattern.compile("^2[aby]$");
    private static final int DEFAULT_BCRYPT_COST = 12;

    public static final String $ = Pattern.quote("$");

    public static boolean isHashedPassword(String postDataPassword) {
        String[] split = postDataPassword.split(Pattern.quote("$"));
        if (split.length != 4)
            return false;
        if (!split[0].isEmpty())
            return false;
        if (split[3].length() < 20)
            return false;
        // Check if the second part is a valid hex
        return HEX_PASSWORD_PATTERN.matcher(postDataPassword).matches();
    }

    static String createPasswdCompatibleHash(String password) {
        byte[] salt = new byte[128];
        secureRandom.nextBytes(salt);

        String saltString = BaseEncoding.base64().encode(salt);
        if (saltString.length() > 16) saltString = saltString.substring(0, 16);

        return createPasswdCompatibleHash(password, saltString);
    }

    public static String createPasswdCompatibleHash(String algo, String password, String salt) {
        if (algo != null && !BCRYPT_ID_PATTERN.matcher(algo).matches())
            return Crypt.crypt(password, "$" + algo + "$" + salt);

        int cost = parseBcryptCostOrDefault(salt, DEFAULT_BCRYPT_COST);

        byte[] bcryptSalt = new byte[16]; // BCrypt salt is always 128-bit
        secureRandom.nextBytes(bcryptSalt);

        return OpenBSDBCrypt.generate(algo, password.toCharArray(), bcryptSalt, cost);

    }

    public static String createPasswdCompatibleHash(String password, String saltString) {
        return createPasswdCompatibleHash("6", password, saltString);
    }

    private static int parseBcryptCostOrDefault(String saltOrCost, int defaultCost) {
        if (saltOrCost == null) return defaultCost;

        if (saltOrCost.matches("\\d{1,2}")) return Integer.parseInt(saltOrCost);

        try {
            String[] p = saltOrCost.split("\\$");
            if (p.length >= 4 && p[0].isEmpty() && p[1].matches("2[aby]")) {
                return Integer.parseInt(p[2]);
            }
        } catch (Exception ignored) {
        }

        return defaultCost;
    }

    /**
     * Returns the hash "magic" / algorithm identifier (e.g. "6" for SHA-512) from a crypt(3) formatted hash: $<id>$<salt>$...
     * @throws IllegalArgumentException if the input is not in crypt(3) hash notation
     */
    public static String getCryptAlgorithmId(String password) {
        try{
            return password.split($)[1];
        } catch (Exception e) {
            throw new IllegalArgumentException("Hash must be in crypt(3) notation: $<id>$<salt>$<hash>", e);
        }
    }

    /**
     * Returns the salt from a crypt(3) formatted hash: $<id>$<salt>$...
     * @throws IllegalArgumentException if the input is not in crypt(3) hash notation
     */
    public static String getCryptSalt(String cryptHash) {
        try {
            return cryptHash.split($)[2];
        } catch (Exception e) {
            throw new IllegalArgumentException("Hash must be in crypt(3) notation: $<id>$<salt>$<hash>", e);
        }
    }

}
