package com.predic8.membrane.core.util;

/* Copyright 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

import com.predic8.membrane.core.interceptor.authentication.session.StaticUserDataProvider.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import static com.predic8.membrane.core.util.SecurityUtils.*;
import static com.predic8.membrane.core.util.SecurityUtils.extractSalt;
import static org.junit.jupiter.api.Assertions.*;

class SecurityUtilsTest {

    @Nested
    class IsHashedPasswordTest {

        @Test
        void validHashedPassword() {
            // Given - Valid SHA-512 hashed password format: $6$salt$hash
            String validHash = "$6$somesalt$abcdefghijklmnopqrstuvwxyz123456";

            // When & Then
            assertTrue(isHashedPassword(validHash));
        }

        @Test
        void validHashedPasswordWithLongHash() {
            // Given
            String validHash = "$6$saltsaltsal$hash123456789012345678901234567890123456789012345678901234567890";

            // When & Then
            assertTrue(SecurityUtils.isHashedPassword(validHash));
        }

        @Test
        void invalidHashedPasswordTooFewParts() {
            // Given - Only 3 parts instead of 4
            String invalidHash = "$6$somesalt";

            // When & Then
            assertFalse(isHashedPassword(invalidHash));
        }

        @Test
        void invalidHashedPasswordNonEmptyFirstPart() {
            // Given - First part should be empty
            String invalidHash = "x$6$somesalt$hash12345678901234567890";

            // When & Then
            assertFalse(isHashedPassword(invalidHash));
        }

        @Test
        void invalidHashedPasswordHashTooShort() {
            // Given - Hash part is less than 20 characters
            String invalidHash = "$6$somesalt$short";

            // When & Then
            assertFalse(isHashedPassword(invalidHash));
        }

        @Test
        void invalidHashedPasswordExactly19Chars() {
            // Given - Hash part is exactly 19 characters (boundary test)
            String invalidHash = "$6$somesalt$1234567890123456789";

            // When & Then
            assertFalse(isHashedPassword(invalidHash));
        }

        @Test
        void validHashedPasswordExactly20Chars() {
            // Given - Hash part is exactly 20 characters (boundary test)
            String validHash = "$6$somesalt$12345678901234567890";

            // When & Then
            assertTrue(isHashedPassword(validHash));
        }

        @Test
        void invalidHashedPasswordTooManyParts() {
            // Given - More than 4 parts
            String invalidHash = "$6$somesalt$hash12345678901234567890$extra";

            // When & Then
            assertFalse(isHashedPassword(invalidHash));
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "$5$anothersalt$abcdefghijklmnopqrstuvwxyz",
                "$1$md5salt$12345678901234567890123456"
        })
        void validHashedPasswordDifferentAlgorithms(String hash) {
            // When & Then
            assertTrue(isHashedPassword(hash));
        }

        @Test
        void plainTextPasswordIsNotHashed() {
            // Given
            String plainPassword = "myPlainTextPassword123";

            // When & Then
            assertFalse(isHashedPassword(plainPassword));
        }

        @Test
        void emptyStringIsNotHashed() {
            // Given
            String empty = "";

            // When & Then
            assertFalse(isHashedPassword(empty));
        }
    }

    @Nested
    class ExtractSaltTest {

        @Test
        void extractSaltFromValidHash() {
            // Given
            String hash = "$6$mysalt$hashhashhashhashhashhash";

            // When
            String salt = extractSalt(hash);

            // Then
            assertEquals("mysalt", salt);
        }

        @Test
        void extractSaltWithRounds() {
            // Given
            String hash = "$6$rounds=5000$saltwithinfo$hashhashhashhashhashhash";

            // When
            String salt = extractSalt(hash);

            // Then
            assertEquals("rounds=5000", salt);
        }

        @Test
        void extractSaltWithSpecialCharacters() {
            // Given
            String hash = "$6$salt.with/chars$hashhashhashhashhashhash";

            // When
            String salt = extractSalt(hash);

            // Then
            assertEquals("salt.with/chars", salt);
        }

        @Test
        void extractSaltEmptySalt() {
            assertEquals("", extractSalt("$6$$hashhashhashhashhashhash"));
        }
    }

    @Nested
    class CreatePasswdCompatibleHashTest {

        @Test
        void createHashWithAlgoSalt() {
            // Given
            AlgoSalt algoSalt = new AlgoSalt("6", "testsalt");
            String password = "mypassword";

            // When
            String hash = SecurityUtils.createPasswdCompatibleHash(algoSalt, password);

            // Then
            assertNotNull(hash);
            assertTrue(hash.startsWith("$6$testsalt$"));
            assertTrue(isHashedPassword(hash));
        }

        @Test
        void createHashWithSaltStringUsesAlgo6() {
            // Given
            String salt = "mysalt";
            String password = "testpassword";

            // When
            String hash = SecurityUtils.createPasswdCompatibleHash(password, salt);

            // Then
            assertNotNull(hash);
            assertTrue(hash.startsWith("$6$mysalt$"));
            assertTrue(isHashedPassword(hash));
        }

        @Test
        void createHashWithDifferentAlgorithm() {
            // Given
            AlgoSalt algoSalt = new AlgoSalt("5", "testsalt");
            String password = "mypassword";

            // When
            String hash = SecurityUtils.createPasswdCompatibleHash(algoSalt, password);

            // Then
            assertNotNull(hash);
            assertTrue(hash.startsWith("$5$testsalt$"));
        }

        @Test
        void samePasswordSameSaltProducesSameHash() {
            // Given
            AlgoSalt algoSalt = new AlgoSalt("6", "fixedsalt");
            String password = "password123";

            // When
            String hash1 = SecurityUtils.createPasswdCompatibleHash(algoSalt, password);
            String hash2 = SecurityUtils.createPasswdCompatibleHash(algoSalt, password);

            // Then
            assertEquals(hash1, hash2);
        }

        @Test
        void differentPasswordsProduceDifferentHashes() {
            // Given
            AlgoSalt algoSalt = new AlgoSalt("6", "fixedsalt");
            String password1 = "password123";
            String password2 = "password456";

            // When
            String hash1 = SecurityUtils.createPasswdCompatibleHash(algoSalt, password1);
            String hash2 = SecurityUtils.createPasswdCompatibleHash(algoSalt, password2);

            // Then
            assertNotEquals(hash1, hash2);
        }

        @Test
        void differentSaltsProduceDifferentHashes() {
            // Given
            AlgoSalt algoSalt1 = new AlgoSalt("6", "salt1");
            AlgoSalt algoSalt2 = new AlgoSalt("6", "salt2");
            String password = "password123";

            // When
            String hash1 = SecurityUtils.createPasswdCompatibleHash(algoSalt1, password);
            String hash2 = SecurityUtils.createPasswdCompatibleHash(algoSalt2, password);

            // Then
            assertNotEquals(hash1, hash2);
        }

        @Test
        void createdHashCanBeVerified() {
            // Given
            String password = "testpassword";
            String salt = "testsalt";
            String hash = SecurityUtils.createPasswdCompatibleHash(password, salt);

            // When - Recreate hash with same password and salt
            String verifyHash = SecurityUtils.createPasswdCompatibleHash(password, salt);

            // Then
            assertEquals(hash, verifyHash);
        }

        @Test
        void createHashWithEmptyPassword() {
            // Given
            AlgoSalt algoSalt = new AlgoSalt("6", "testsalt");
            String password = "";

            // When
            String hash = SecurityUtils.createPasswdCompatibleHash(algoSalt, password);

            // Then
            assertNotNull(hash);
            assertTrue(hash.startsWith("$6$testsalt$"));
        }
    }

    @Nested
    class ExtractMagicStringTest {

        @Test
        void extractMagicStringFromValidHash() {
            // Given
            String hash = "$6$somesalt$hashhashhashhashhashhash";

            // When
            String magicString = SecurityUtils.extractMagicString(hash);

            // Then
            assertEquals("6", magicString);
        }

        @Test
        void extractMagicStringAlgo5() {
            // Given
            String hash = "$5$somesalt$hashhashhashhashhashhash";

            // When
            String magicString = SecurityUtils.extractMagicString(hash);

            // Then
            assertEquals("5", magicString);
        }

        @Test
        void extractMagicStringAlgo1() {
            // Given
            String hash = "$1$somesalt$hashhashhashhashhashhash";

            // When
            String magicString = SecurityUtils.extractMagicString(hash);

            // Then
            assertEquals("1", magicString);
        }

        @Test
        void extractMagicStringBcrypt() {
            // Given
            String hash = "$2a$10$saltsalt$hashhashhashhashhashhash";

            // When
            String magicString = SecurityUtils.extractMagicString(hash);

            // Then
            assertEquals("2a", magicString);
        }

        @Test
        void throwsExceptionOnInvalidFormat() {
            // Given
            String invalidHash = "notahash";

            // When & Then
            RuntimeException exception = assertThrows(RuntimeException.class,
                    () -> SecurityUtils.extractMagicString(invalidHash));
            assertTrue(exception.getMessage().contains("Password must be in hash notation"));
        }

        @Test
        void throwsExceptionOnNoDelimiter() {
            // Given
            String invalidHash = "noDollarSign";

            // When & Then
            RuntimeException exception = assertThrows(RuntimeException.class,
                    () -> SecurityUtils.extractMagicString(invalidHash));
            assertTrue(exception.getMessage().contains("Password must be in hash notation"));
        }

        @Test
        void throwsExceptionWithEmptyString() {
            // Given
            String emptyHash = "";

            // When & Then
            RuntimeException exception = assertThrows(RuntimeException.class,
                    () -> SecurityUtils.extractMagicString(emptyHash));
            assertTrue(exception.getMessage().contains("Password must be in hash notation"));
        }
    }

    @Nested
    class IntegrationTest {

        @Test
        void fullHashWorkflow() {
            // Given
            String password = "mySecurePassword123";
            String salt = "randomsalt";

            // When - Create hash
            String hash = SecurityUtils.createPasswdCompatibleHash(password, salt);

            // Then - Verify hash properties
            assertTrue(isHashedPassword(hash));
            assertEquals("6", SecurityUtils.extractMagicString(hash));
            assertEquals(salt, extractSalt(hash));

            // When - Verify password
            String verifyHash = SecurityUtils.createPasswdCompatibleHash(password, salt);

            // Then
            assertEquals(hash, verifyHash);
        }

        @Test
        void hashedPasswordRoundTrip() {
            // Given
            String originalPassword = "testPassword";
            AlgoSalt algoSalt = new AlgoSalt("6", "mysalt");

            // When
            String hashedPassword = SecurityUtils.createPasswdCompatibleHash(algoSalt, originalPassword);
            String extractedAlgo = SecurityUtils.extractMagicString(hashedPassword);
            String extractedSalt = extractSalt(hashedPassword);

            // Then
            assertEquals("6", extractedAlgo);
            assertEquals("mysalt", extractedSalt);

            // When - Recreate with extracted values
            AlgoSalt reconstructedAlgoSalt = new AlgoSalt(extractedAlgo, extractedSalt);
            String rehashedPassword = SecurityUtils.createPasswdCompatibleHash(reconstructedAlgoSalt, originalPassword);

            // Then
            assertEquals(hashedPassword, rehashedPassword);
        }

        @Test
        void wrongPasswordProducesDifferentHash() {
            // Given
            String correctPassword = "correct";
            String wrongPassword = "wrong";
            String salt = "salt";

            // When
            String correctHash = SecurityUtils.createPasswdCompatibleHash(correctPassword, salt);
            String wrongHash = SecurityUtils.createPasswdCompatibleHash(wrongPassword, salt);

            // Then
            assertNotEquals(correctHash, wrongHash);
        }
    }

    @Nested
    class PatternTest {

        @Test
        void hexPasswordPatternMatches() {
            // Given
            String validHash = "$6$somesalt$abcdef1234567890abcdef";

            // When & Then
            assertTrue(SecurityUtils.HEX_PASSWORD_PATTERN.matcher(validHash).matches());
        }

        @Test
        void hexPasswordPatternMatchesWithNumbers() {
            // Given
            String validHash = "$6$123456$abcdef1234567890abcdef";

            // When & Then
            assertTrue(SecurityUtils.HEX_PASSWORD_PATTERN.matcher(validHash).matches());
        }

        @Test
        void hexPasswordPatternDoesNotMatchInvalid() {
            // Given
            String invalidHash = "notavalidhash";

            // When & Then
            assertFalse(SecurityUtils.HEX_PASSWORD_PATTERN.matcher(invalidHash).matches());
        }
    }
}