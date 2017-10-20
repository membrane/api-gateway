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
        if (split.length != 4) {
            return false;
        }
        if (!split[0].isEmpty()) {
            return false;
        }
        if (split[3].length() < 20) {
            return false;
        }
        return true;
    }

    public static String createPasswdCompatibleHash(String password) throws UnsupportedEncodingException, NoSuchAlgorithmException {
        byte[] salt = new byte[128];
        new SecureRandom().nextBytes(salt);

        String saltString = Base64.encodeBase64String(salt);
        if (saltString.length() > 16) {
            saltString = saltString.substring(0, 16);
        }

        saltString.replaceAll(Pattern.quote("+"), Pattern.quote("."));
        saltString = "$6$" + saltString;

        return Crypt.crypt(password, saltString);
    }
}
