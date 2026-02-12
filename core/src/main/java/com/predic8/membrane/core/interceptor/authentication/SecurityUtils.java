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

package com.predic8.membrane.core.interceptor.authentication;

import org.apache.commons.codec.digest.Crypt;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.generators.OpenBSDBCrypt;
import org.bouncycastle.crypto.params.Argon2Parameters;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.security.MessageDigest.isEqual;
import static java.util.regex.Pattern.compile;
import static org.bouncycastle.crypto.generators.OpenBSDBCrypt.checkPassword;
import static org.bouncycastle.crypto.params.Argon2Parameters.ARGON2_id;
import static org.bouncycastle.util.Arrays.constantTimeAreEqual;

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

    // argon2id with strict parameter order
    private static final Pattern ARGON2ID_PATTERN = compile("^\\$argon2id\\$v=(\\d+)\\$m=(\\d+),t=(\\d+),p=(\\d+)\\$([^$]+)\\$([^$]+)$");

    public static final String PASSWORD = "password";

    private SecurityUtils() {}

    public static boolean matchesHashPattern(String s) {
        if (s == null) return false;
        return BCRYPT_PATTERN.matcher(s).matches() || CRYPT3_PATTERN.matcher(s).matches() || s.startsWith("$argon2id$");
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

        if (storedHashOrPlain.startsWith("$argon2id$")) {
            return verifyArgon2id(plaintext, storedHashOrPlain);
        }

        return isEqual(storedHashOrPlain.getBytes(), plaintext.getBytes());
    }

    public static String buildArgon2idPCH(byte[] password, byte[] salt, int version, int iterations, int memory, int parallelism) {
        byte[] hash = hashPasswordArgon2id(password, salt, version, iterations, memory, parallelism);
        Base64.Encoder encoder = Base64.getEncoder().withoutPadding();

        return String.format(
                "$argon2id$v=%d$m=%d,t=%d,p=%d$%s$%s",
                version,
                memory,
                iterations,
                parallelism,
                encoder.encodeToString(salt),
                encoder.encodeToString(hash));
    }

    public static boolean verifyArgon2id(String plaintext, String storedHash) {
        Matcher matcher = ARGON2ID_PATTERN.matcher(storedHash);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid hash format. Must follow " + ARGON2ID_PATTERN.pattern());
        }

        int version = Integer.parseInt(matcher.group(1));
        int memory = Integer.parseInt(matcher.group(2));
        int iterations = Integer.parseInt(matcher.group(3));
        int parallelism = Integer.parseInt(matcher.group(4));
        Base64.Decoder decoder = Base64.getDecoder();
        byte[] salt = decoder.decode(matcher.group(5));
        byte[] originalHash = decoder.decode(matcher.group(6));

        return constantTimeAreEqual(hashPasswordArgon2id(plaintext.getBytes(UTF_8), salt, version, iterations, memory, parallelism), originalHash);
    }

    public static byte[] hashPasswordArgon2id(byte[] password, byte[] salt, int version, int iterations, int memory, int parallelism) {
        Argon2Parameters params = new Argon2Parameters.Builder(ARGON2_id).withVersion(version)
                .withIterations(iterations).withMemoryAsKB(memory).withParallelism(parallelism).withSalt(salt).build();

        Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(params);

        byte[] result = new byte[32];
        generator.generateBytes(password, result);
        return result;
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
