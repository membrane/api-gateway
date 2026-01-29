package com.predic8.membrane.core.interceptor.authentication;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

import static com.predic8.membrane.core.interceptor.authentication.SecurityUtils.*;
import static com.predic8.membrane.core.interceptor.authentication.SecurityUtils.PASSWORD;
import static com.predic8.membrane.core.interceptor.authentication.SecurityUtils.hashPasswordBcrypt;
import static com.predic8.membrane.core.interceptor.authentication.SecurityUtils.verifyLoginOrThrow;
import static org.apache.commons.codec.digest.Crypt.crypt;
import static org.bouncycastle.crypto.generators.OpenBSDBCrypt.generate;
import static org.junit.jupiter.api.Assertions.*;

class SecurityUtilsTest {

    private static final String DEMO_PASSWORD = "very>SecureP4$$word";
    private static final String WRONG_PASSWORD = "wrong";

    private static final byte[] FIXED_BCRYPT_SALT_16 = new byte[] {
            (byte)0x01, (byte)0x23, (byte)0x45, (byte)0x67,
            (byte)0x89, (byte)0xab, (byte)0xcd, (byte)0xef,
            (byte)0x10, (byte)0x32, (byte)0x54, (byte)0x76,
            (byte)0x98, (byte)0xba, (byte)0xdc, (byte)0xfe
    };

    @Test
    void matchesHashPattern_and_requirePlaintextPasswordInput_cover_multiple_cases() {
        // crypt(3) without rounds
        String crypt6 = crypt(DEMO_PASSWORD, "$6$testsalt");
        assertTrue(matchesHashPattern(crypt6));
        assertThrows(IllegalArgumentException.class, () -> requirePlaintextPasswordInput(crypt6));

        // crypt(3) with rounds
        String crypt6Rounds = crypt(DEMO_PASSWORD, "$6$rounds=5000$testsalt");
        assertTrue(matchesHashPattern(crypt6Rounds));
        assertThrows(IllegalArgumentException.class, () -> requirePlaintextPasswordInput(crypt6Rounds));

        // bcrypt (deterministic via fixed salt)
        String bcrypt = generate("2y", DEMO_PASSWORD.toCharArray(), FIXED_BCRYPT_SALT_16, 10);
        assertTrue(matchesHashPattern(bcrypt));
        assertThrows(IllegalArgumentException.class, () -> requirePlaintextPasswordInput(bcrypt));

        assertFalse(matchesHashPattern(DEMO_PASSWORD));
        assertDoesNotThrow(() -> requirePlaintextPasswordInput(DEMO_PASSWORD));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2a", "2b", "2y"})
    void verifyPassword_bcrypt_works(String algo) {
        String stored = generate(algo, DEMO_PASSWORD.toCharArray(), FIXED_BCRYPT_SALT_16, 10);
        assertTrue(verifyPassword(DEMO_PASSWORD, stored));
        assertFalse(verifyPassword(WRONG_PASSWORD, stored));
    }

    @Test
    void verifyPassword_crypt3_works_with_and_without_rounds() {
        // without rounds
        assertCrypt3VerifyOk("$1$testsalt");
        assertCrypt3VerifyOk("$5$testsalt");
        assertCrypt3VerifyOk("$6$testsalt");

        // with rounds (sha256/sha512)
        assertCrypt3VerifyOk("$5$rounds=5000$testsalt");
        assertCrypt3VerifyOk("$6$rounds=5000$testsalt");
    }

    private static void assertCrypt3VerifyOk(String saltPrefix) {
        String stored = crypt(DEMO_PASSWORD, saltPrefix);
        assertTrue(matchesHashPattern(stored), "Expected crypt(3) hash pattern for: " + saltPrefix);
        assertTrue(verifyPassword(DEMO_PASSWORD, stored));
        assertFalse(verifyPassword(WRONG_PASSWORD, stored));
    }

    @Test
    void verifyPassword_plaintextFallback_small() {
        assertTrue(verifyPassword("abc", "abc"));
        assertFalse(verifyPassword("abc", "def"));
    }

    @Test
    void hashPasswordBcrypt_returns_valid_hash_and_verifies_small() {
        String stored = hashPasswordBcrypt("2y", 12, DEMO_PASSWORD);

        assertTrue(matchesHashPattern(stored));
        assertTrue(verifyPassword(DEMO_PASSWORD, stored));
        assertFalse(verifyPassword(WRONG_PASSWORD, stored));

        String[] parts = stored.split("\\$");
        assertEquals("2y", parts[1]);
        assertEquals("12", parts[2]);
    }

    @Test
    void verifyLoginOrThrow_small_matrix() {
        String stored = hashPasswordBcrypt("2y", 10, DEMO_PASSWORD);

        // missing password
        assertThrows(NoSuchElementException.class, () -> verifyLoginOrThrow(new HashMap<>(), stored));

        // hash-looking input (client sends hash as password)
        assertThrows(IllegalArgumentException.class, () -> verifyLoginOrThrow(Map.of(PASSWORD, stored), stored));

        // wrong password
        assertThrows(NoSuchElementException.class, () -> verifyLoginOrThrow(Map.of(PASSWORD, WRONG_PASSWORD), stored));

        // ok
        assertDoesNotThrow(() -> verifyLoginOrThrow(Map.of(PASSWORD, DEMO_PASSWORD), stored));
    }

    @Test
    void hashPasswordBcrypt_randomSalt_still_verifies() {
        String h1 = hashPasswordBcrypt("2y", 12, "foo");
        String h2 = hashPasswordBcrypt("2y", 12, "foo");

        assertNotEquals(h1, h2);
        assertTrue(verifyPassword("foo", h1));
        assertTrue(verifyPassword("foo", h2));
        assertFalse(verifyPassword("bar", h1));
        assertFalse(verifyPassword("bar", h2));
    }

    /**
     * Verifies that {@link SecurityUtils#verifyPassword(String, String)} can validate
     * a set of externally generated hashes against the plaintext password {@code "foo"}.
     */
    @Test
    void verifyPassword_accepts_known_external_hashes_for_foo() {
        String[] knownFooHashes = new String[] {
                "$2y$15$YvsVrHmGZOf/qzUT7JgLl.q0kYSSpWK80fE7D08wZU88rmTnWhZVS", // htpasswd -bnBC 15 "ignored" foo
                "$6$rounds=500000$gzh1tg4O2bM5tm5y$6d2TcRsvONfSZ4lTxwgn1i2fU7phH1ChaTYKfrZbKgIR/nhNoiACNzgU3aqK5geqxNSUlrEd1/pwuChnq93xE/", // mkpasswd -m sha-512 -R 500000
                "$6$mlgc7rlkfaMTil6L$EgrQ2otQe158FQ5EgLgCmiRiWH8RQ.VMpCLoER6kgGg/xgsfDJjiFWoaKU9uI33TG1SQG0lIXUiu1AuwX5WRU0", // openssl passwd -6
                "$5$testsalt$K7uJizXY4KVJstVTRzUISGL4pZ7s4Q.caQ6aA6lwDxB", // openssl passwd -5 -salt testsalt foo
                "$2a$08$GxGhXE6fSCxNRCXYyZcRKe8ucFIhZaRE5YwB32pKeloWMf9ibnBBO" // https://bcrypt-generator.com/ with 8 rounds
        };

        for (String h : knownFooHashes) {
            assertTrue(matchesHashPattern(h), "Expected hash pattern for: " + h);
            assertTrue(verifyPassword("foo", h), "Expected to verify 'foo' for: " + h);
            assertFalse(verifyPassword("bar", h), "Expected 'bar' to fail for: " + h);
        }
    }
}
