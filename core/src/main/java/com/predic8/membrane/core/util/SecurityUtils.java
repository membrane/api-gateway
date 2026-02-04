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

import org.apache.commons.codec.digest.*;

import java.util.regex.*;

public class SecurityUtils {

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

    public static String createPasswdCompatibleHash(AlgoSalt as, String password) {
        return Crypt.crypt(password, "$%s$%s".formatted(as.algo(), as.salt()));
    }

    public static String createPasswdCompatibleHash(String password, String saltString) {
        return createPasswdCompatibleHash(new AlgoSalt("6", saltString), password);
    }

    public static String extractMagicString(String password) {
        try {
            return password.split(Pattern.quote("$"))[1];
        } catch (Exception e) {
            throw new RuntimeException("Password must be in hash notation", e);
        }
    }

    public record AlgoSalt(String algo, String salt) {
        public static AlgoSalt from(String userHash) {
            String[] userHashSplit = userHash.split(Pattern.quote("$"));
            if (userHashSplit.length < 3) {
                throw new IllegalArgumentException("Invalid hash format: %s at least 3 dollar separated parts required, got: %d".formatted(userHash, userHashSplit.length));
            }
            return new AlgoSalt(userHashSplit[1], userHashSplit[2]);
        }
    }
}
