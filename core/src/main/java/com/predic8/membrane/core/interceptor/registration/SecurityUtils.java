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

import com.google.api.client.repackaged.org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.Crypt;

import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.regex.Pattern;

/**
 * Created by Martin Dünkelmann(duenkelmann@predic8.de) on 20.10.17.
 */
public class SecurityUtils {
    public static boolean isHashedPassword(String postDataPassword) {
        // TODO do a better check here
        String[] split = postDataPassword.split(Pattern.quote("$"));
        return split.length == 4 && split[0].isEmpty() && split[3].length() >= 20;
    }

    static String createPasswdCompatibleHash(String password) throws UnsupportedEncodingException, NoSuchAlgorithmException {
        byte[] salt = new byte[128];
        new SecureRandom().nextBytes(salt);

        String saltString = Base64.encodeBase64String(salt);
        if (saltString.length() > 16) saltString = saltString.substring(0, 16);

        return createPasswdCompatibleHash(password, saltString);
    }

    public static String extractSalt(String password) {
        return password.split(Pattern.quote("$"))[2];
    }

    public static String createPasswdCompatibleHash(String password, String saltString) {
        saltString = "$6$" + saltString;
        return Crypt.crypt(password, saltString);
    }
}
