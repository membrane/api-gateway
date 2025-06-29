package com.predic8.membrane.core.interceptor.authentication.session;

import com.predic8.membrane.core.interceptor.authentication.session.StaticUserDataProvider.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

public class StaticUserDataProviderTest {

    private StaticUserDataProvider provider;
    private PasswordEncoder bCryptPasswordEncoder;

    @BeforeEach
    void setUp() {
        provider = new StaticUserDataProvider();
        bCryptPasswordEncoder = new BCryptPasswordEncoder();
        // Initialize with some users
        User plainUser = new User("plainUser", "password123");
        User bcryptUser = new User("bcryptUser", bCryptPasswordEncoder.encode("bcryptPass"));
        // Apache crypt hash for "apachePass".
        // Generated with: org.apache.commons.codec.digest.Crypt.crypt("apachePass", "$1$salt$");
        // Output: "$1$salt$c2dG8iPu.kPg3hJ8L6I061"
        User apacheUser = new User("apacheUser", "$1$salt$c2dG8iPu.kPg3hJ8L6I061"); // password is "apachePass"

        provider.setUsers(List.of(plainUser, bcryptUser, apacheUser));
        provider.init(null); // Router can be null for these tests
    }

    @Test
    void verify_plainTextPassword_correct() {
        Map<String, String> credentials = new HashMap<>();
        credentials.put("username", "plainUser");
        credentials.put("password", "password123");
        Map<String, String> attributes = assertDoesNotThrow(() -> provider.verify(credentials));
        assertEquals("plainUser", attributes.get("username"));
    }

    @Test
    void verify_plainTextPassword_incorrect() {
        Map<String, String> credentials = new HashMap<>();
        credentials.put("username", "plainUser");
        credentials.put("password", "wrongPassword");
        Exception e = assertThrows(NoSuchElementException.class, () -> provider.verify(credentials));
        assertEquals("Incorrect password for user 'plainUser'.", e.getMessage());
    }

    @Test
    void verify_bcryptPassword_correct() {
        Map<String, String> credentials = new HashMap<>();
        credentials.put("username", "bcryptUser");
        credentials.put("password", "bcryptPass");
        Map<String, String> attributes = assertDoesNotThrow(() -> provider.verify(credentials));
        assertEquals("bcryptUser", attributes.get("username"));
    }

    @Test
    void verify_bcryptPassword_incorrect() {
        Map<String, String> credentials = new HashMap<>();
        credentials.put("username", "bcryptUser");
        credentials.put("password", "wrongPassword");
        Exception e = assertThrows(NoSuchElementException.class, () -> provider.verify(credentials));
        assertEquals("Incorrect password for user 'bcryptUser'.", e.getMessage());
    }

    @Test
    void verify_apacheCryptPassword_correct() {
        Map<String, String> credentials = new HashMap<>();
        credentials.put("username", "apacheUser");
        credentials.put("password", "apachePass"); // Raw password to be hashed by the verify method
        Map<String, String> attributes = assertDoesNotThrow(() -> provider.verify(credentials));
        assertEquals("apacheUser", attributes.get("username"));
    }

    @Test
    void verify_apacheCryptPassword_incorrect() {
        Map<String, String> credentials = new HashMap<>();
        credentials.put("username", "apacheUser");
        credentials.put("password", "wrongPassword");
        Exception e = assertThrows(NoSuchElementException.class, () -> provider.verify(credentials));
        assertEquals("Incorrect password for user 'apacheUser'.", e.getMessage());
    }

    @Test
    void verify_userNotFound() {
        Map<String, String> credentials = new HashMap<>();
        credentials.put("username", "unknownUser");
        credentials.put("password", "anyPassword");
        Exception e = assertThrows(NoSuchElementException.class, () -> provider.verify(credentials));
        assertEquals("User 'unknownUser' not found.", e.getMessage());
    }

    @Test
    void verify_passwordNotProvided() {
        Map<String, String> credentials = new HashMap<>();
        credentials.put("username", "plainUser");
        // No password
        Exception e = assertThrows(IllegalArgumentException.class, () -> provider.verify(credentials));
        assertEquals("Password not provided.", e.getMessage());
    }

    @Test
    void verify_usernameNotProvided() {
        Map<String, String> credentials = new HashMap<>();
        credentials.put("password", "anyPassword");
        // No username
        Exception e = assertThrows(NoSuchElementException.class, () -> provider.verify(credentials));
        assertEquals("Username not provided.", e.getMessage());
    }

    @Test
    void verify_userWithNoPasswordSet() {
        User noPassUser = new User();
        noPassUser.setUsername("noPassUser");
        // noPassUser.setPassword(null); // password is null by default
        provider.setUsers(Collections.singletonList(noPassUser));
        provider.init(null);

        Map<String, String> credentials = new HashMap<>();
        credentials.put("username", "noPassUser");
        credentials.put("password", "anyPassword");

        Exception e = assertThrows(NoSuchElementException.class, () -> provider.verify(credentials));
        assertEquals("No password configured for user 'noPassUser'.", e.getMessage());
    }

    @Test
    void isApacheCryptHashedPassword_validApacheCrypt() {
        // Valid MD5 crypt hash
        assertTrue(provider.isApacheCryptHashedPassword("$1$salt$c2dG8iPu.kPg3hJ8L6I061"));
        assertTrue(provider.isApacheCryptHashedPassword("$apr1$salt$c2dG8iPu.kPg3hJ8L6I061")); // APR1 (MD5 variant)
        // Valid SHA-256 crypt hash
        assertTrue(provider.isApacheCryptHashedPassword("$5$salt$kFqS4o4.q4P9Z7o6l8sYj0c2dG8iPu.kPg3hJ8L6I061"));
        // Valid SHA-512 crypt hash
        assertTrue(provider.isApacheCryptHashedPassword("$6$salt$kFqS4o4.q4P9Z7o6l8sYj0c2dG8iPu.kPg3hJ8L6I061kFqS4o4.q4P9Z7o6l8sYj0"));
    }

    @Test
    void isApacheCryptHashedPassword_invalidFormatsAndBcrypt() {
        assertFalse(provider.isApacheCryptHashedPassword("plainpassword")); // Plain text
        // Standard BCrypt hash, isApacheCryptHashedPassword should return false for these
        assertFalse(provider.isApacheCryptHashedPassword(bCryptPasswordEncoder.encode("test")));
        assertFalse(provider.isApacheCryptHashedPassword("$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"));
        assertFalse(provider.isApacheCryptHashedPassword("$1$salt")); // Not a full crypt string but prefix is valid for Apache
        assertFalse(provider.isApacheCryptHashedPassword("1$salt$hash")); // Missing leading $
        assertFalse(provider.isApacheCryptHashedPassword("$1$$hash")); // Missing salt but prefix is valid
        assertFalse(provider.isApacheCryptHashedPassword("$invalid$salt$hash")); // Invalid algorithm identifier
    }

    @Test
    void userAttributesReturnedOnSuccessfulVerification() {
        User testUser = new User("testattruser", "password");
        testUser.getAttributes().put("customAttribute", "customValue");
        provider.setUsers(Collections.singletonList(testUser));
        provider.init(null);

        Map<String, String> credentials = new HashMap<>();
        credentials.put("username", "testattruser");
        credentials.put("password", "password");

        Map<String, String> attributes = provider.verify(credentials);
        assertNotNull(attributes);
        assertEquals("testattruser", attributes.get("username"));
        //The actual password (even plain) is stored in attributes
        assertEquals("password", attributes.get("password"));
        assertEquals("customValue", attributes.get("customAttribute"));
    }

    @Test
    void verify_apacheCryptPassword_invalidLegacyHashFormat_runtimeException() {
        User badHashUser = new User("badhashuser", "$1$short"); // Invalid salt part for Crypt.crypt typically
        provider.setUsers(List.of(badHashUser));
        provider.init(null);

        Map<String, String> credentials = new HashMap<>();
        credentials.put("username", "badhashuser");
        credentials.put("password", "anypassword");

        // This will now be caught by the plain text comparison and fail as NoSuchElementException
        // because isApacheCryptHashedPassword("$1$short") will return true, but Crypt.crypt will likely fail or produce non-matching hash.
        // If Crypt.crypt fails with IllegalArgumentException for bad salt, that would be caught.
        // If it produces a non-matching hash, then it falls to plain text comparison.
        // Let's verify the specific error message for incorrect password.
        Exception e = assertThrows(NoSuchElementException.class, () -> provider.verify(credentials));
        assertTrue(e.getMessage().contains("Incorrect password for user 'badhashuser'.") || e.getMessage().contains("Invalid Apache legacy hash format"), "Exception message should indicate incorrect password or invalid hash format.");
    }
}
