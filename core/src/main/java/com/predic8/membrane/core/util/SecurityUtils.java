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

package com.predic8.membrane.core.util;

import com.google.common.io.BaseEncoding;
import org.apache.commons.codec.digest.Crypt;

import java.security.SecureRandom;
import java.util.regex.Pattern;

public class SecurityUtils {

    private static final SecureRandom secureRandom = new SecureRandom();
    public static final Pattern HEX_PASSWORD_PATTERN = Pattern.compile("\\$([^$]+)\\$([^$]+)\\$.+");
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

    public static String extractSalt(String password) {
        return password.split($)[2];
    }

    public static String createPasswdCompatibleHash(String algo, String password, String salt) {
        return Crypt.crypt(password, "$" + algo + "$" + salt);
    }

    public static String createPasswdCompatibleHash(String password, String saltString) {
        return createPasswdCompatibleHash("6", password, saltString);
    }

    public static String extractMagicString(String password) {
        try{
            return password.split(Pattern.quote("$"))[1];
        } catch (Exception e) {
            throw new RuntimeException("Password must be in hash notation", e);
        }
    }
}
