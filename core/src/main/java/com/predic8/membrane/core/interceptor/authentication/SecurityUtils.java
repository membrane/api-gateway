package com.predic8.membrane.core.interceptor.authentication;

import org.apache.commons.codec.digest.Crypt;
import org.bouncycastle.crypto.generators.OpenBSDBCrypt;

import java.security.SecureRandom;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.regex.Pattern;

import static java.security.MessageDigest.isEqual;
import static java.util.regex.Pattern.compile;
import static org.bouncycastle.crypto.generators.OpenBSDBCrypt.checkPassword;

/**
 * Password hashing/verification helper.
 * <p>
 * Supported hash formats:
 * - bcrypt (MCF): $2a$, $2b$, $2y$ e.g. "$2y$12$..."
 * - crypt(3) (MCF): $<id>$<salt>$<hash> where <id> is typically:
 *     - 1 = md5-crypt
 *     - 5 = sha256-crypt
 *     - 6 = sha512-crypt
 * - crypt(3) with rounds: $<id>$rounds=<n>$<salt>$<hash> (sha256/sha512)
 * <p>
 * Notes:
 * - Client input must be plaintext; hash-looking password inputs are rejected.
 * - Apache htpasswd MD5 format "$apr1$..." is not supported.
 */
public final class SecurityUtils {

    private static final SecureRandom secureRandom = new SecureRandom();

    // Supports: $<id>$<salt>$<hash> and $<id>$rounds=<n>$<salt>$<hash>
    private static final Pattern CRYPT3_PATTERN = compile("^\\$([^$]{1,8})\\$(?:rounds=\\d+\\$)?([^$]{1,64})\\$([^$]{20,})$");

    // bcrypt: $2a$10$<53 chars>
    private static final Pattern BCRYPT_PATTERN = compile("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");
    public static final String PASSWORD = "password";

    private SecurityUtils() {}

    public static boolean matchesHashPattern(String s) {
        if (s == null) return false;
        return BCRYPT_PATTERN.matcher(s).matches() || CRYPT3_PATTERN.matcher(s).matches();
    }

    public static void requirePlaintextPasswordInput(String password) {
        if (matchesHashPattern(password)) {
            throw new IllegalArgumentException("Refusing hash-looking password input. Send plaintext only.");
        }
    }

    public static boolean verifyPassword(String plaintext, String storedHashOrPlain) {
        if (BCRYPT_PATTERN.matcher(storedHashOrPlain).matches()) {
            return checkPassword(storedHashOrPlain, plaintext.toCharArray());
        }

        // crypt(3) family ($id$salt$hash)
        if (CRYPT3_PATTERN.matcher(storedHashOrPlain).matches()) {
            return Crypt.crypt(plaintext, storedHashOrPlain).equals(storedHashOrPlain);
        }

        return isEqual(storedHashOrPlain.getBytes(), plaintext.getBytes());
    }

    public static String hashPasswordBcrypt(String algo, int cost, String plaintext) {
        byte[] salt = new byte[16];
        secureRandom.nextBytes(salt);
        return OpenBSDBCrypt.generate(algo, plaintext.toCharArray(), salt, cost);
    }

    public static void verifyLoginOrThrow(Map<String, String> postData, String storedPassword) {
        String password = postData.get(PASSWORD);
        if (password == null) throw new NoSuchElementException();

        requirePlaintextPasswordInput(password);

        if (storedPassword == null || !verifyPassword(password, storedPassword))
            throw new NoSuchElementException();
    }

}
