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
package com.predic8.membrane.core.interceptor.authentication.session;

import com.predic8.membrane.core.router.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static com.predic8.membrane.core.util.SecurityUtils.*;
import static org.junit.jupiter.api.Assertions.*;

public class StaticUserDataProviderTest {

    private StaticUserDataProvider provider;
    private Router router;

    @BeforeEach
    void setUp() {
        provider = new StaticUserDataProvider();
        router = new DummyTestRouter();
    }

    @Test
    void verifyWithValidCredentials() {
        // Given
        provider.setUsers(List.of(new User("alice", "secret123")));

        var postData = Map.of(
                "username", "alice",
                "password", "secret123"
        );

        // When
        var result = provider.verify(postData);

        // Then
        assertNotNull(result);
        assertEquals("alice", result.get("username"));
        assertNull( result.get("password"));
    }

    @Test
    void verifyWithInvalidPassword() {
        // Given
        provider.setUsers(List.of(new User("bob", "correctPassword")));

        var postData = Map.of(
                "username", "bob",
                "password", "wrongPassword"
        );

        // When & Then
        assertThrows(NoSuchElementException.class, () -> provider.verify(postData));
    }

    @Test
    void verifyWithNonExistentUser() {
        // Given
        provider.setUsers(List.of(new User("alice", "secret")));

        var postData = Map.of(
                "username", "nonexistent",
                "password", "secret"
        );

        // When & Then
        assertThrows(NoSuchElementException.class, () -> provider.verify(postData));
    }

    @Test
    void testVerifyWithNullUsername() {
        // Given
        Map<String, String> postData = Map.of("password", "secret");

        // When & Then
        assertThrows(NoSuchElementException.class, () -> provider.verify(postData));
    }

    @Test
    void verifyWithHashedPassword() {
        // Given - SHA-512 hash format: $6$salt$hash
        provider.setUsers(List.of(new User("alice", "$6$somesalt$12345678901234567890abcdef")));


        Map<String, String> postData = Map.of(
                "username", "alice",
                "password", "plaintext"
        );

        // When & Then - Should fail because plaintext doesn't match the hash
        assertThrows(NoSuchElementException.class, () -> provider.verify(postData));
    }

    @Test
    void verifyReturnsAllUserAttributes() {
        // Given
        var user = new User("alice", "secret");
        user.setAttributes(Map.of("headerRole", "admin", "email", "alice@example.com"));
        provider.setUsers(List.of(user));

        var postData = Map.of(
                "username", "alice",
                "password", "secret"
        );

        // When
        var result = provider.verify(postData);

        // Then
        assertEquals("alice", result.get("username"));
        assertNull(result.get("password"));
        assertEquals("admin", result.get("headerRole"));
        assertEquals("alice@example.com", result.get("email"));
    }

    @Test
    void setUsersPopulatesUsersByName() {
        // Given
        User user1 = new User("alice", "pass1");
        User user2 = new User("bob", "pass2");
        List<User> users = List.of(user1, user2);

        // When
        provider.setUsers(users);

        // Then
        assertEquals(2, provider.getUsersByName().size());
        assertEquals(user1, provider.getUsersByName().get("alice"));
        assertEquals(user2, provider.getUsersByName().get("bob"));
    }

    @Test
    void setUsersClearsExistingUsers() {
        // Given
        User user1 = new User("alice", "pass1");
        provider.setUsers(List.of(user1));

        User user2 = new User("bob", "pass2");

        // When
        provider.setUsers(List.of(user2));

        // Then
        assertEquals(1, provider.getUsersByName().size());
        assertNull(provider.getUsersByName().get("alice"));
        assertEquals(user2, provider.getUsersByName().get("bob"));
    }

    @Test
    void initPopulatesUsersByName() {
        // Given
        User user1 = new User("alice", "pass1");
        User user2 = new User("bob", "pass2");
        provider.getUsers().add(user1);
        provider.getUsers().add(user2);

        // When
        provider.init(router);

        // Then
        assertEquals(2, provider.getUsersByName().size());
        assertEquals(user1, provider.getUsersByName().get("alice"));
        assertEquals(user2, provider.getUsersByName().get("bob"));
    }

    @Nested
    class UserTest {

        @Test
        void testConstructorWithParameters() {
            // When
            User user = new User("testuser", "testpass");

            // Then
            assertEquals("testuser", user.getUsername());
            assertEquals("testpass", user.getPassword());
        }
    }

    @Nested
    class AlgoSaltTest {

        @Test
        void fromParsesHashedPassword() {
            // Given
            var hashedPassword = "$6$somesalt$actualhashedvalue";

            // When
            var algoSalt = AlgoSalt.from(hashedPassword);

            // Then
            assertEquals("6", algoSalt.algo());
            assertEquals("somesalt", algoSalt.salt());
        }

        @Test
        void fromParsesHashedPasswordWithDifferentAlgo() {
            // Given
            String hashedPassword = "$5$differentsalt$hash";

            // When
            AlgoSalt algoSalt = AlgoSalt.from(hashedPassword);

            // Then
            assertEquals("5", algoSalt.algo());
            assertEquals("differentsalt", algoSalt.salt());
        }

        @Test
        void testAlgoSaltRecordEquality() {
            // Given
            AlgoSalt salt1 = new AlgoSalt("6", "salt");
            AlgoSalt salt2 = new AlgoSalt("6", "salt");
            AlgoSalt salt3 = new AlgoSalt("5", "salt");

            // Then
            assertEquals(salt1, salt2);
            assertNotEquals(salt1, salt3);
        }
    }

    @Test
    void multipleUsersVerification() {
        // Given
        User alice = new User("alice", "pass1");
        User bob = new User("bob", "pass2");
        User charlie = new User("charlie", "pass3");
        provider.setUsers(List.of(alice, bob, charlie));

        // When & Then - Verify Alice
        Map<String, String> aliceData = Map.of("username", "alice", "password", "pass1");
        Map<String, String> aliceResult = provider.verify(aliceData);
        assertEquals("alice", aliceResult.get("username"));

        // When & Then - Verify Bob
        Map<String, String> bobData = Map.of("username", "bob", "password", "pass2");
        Map<String, String> bobResult = provider.verify(bobData);
        assertEquals("bob", bobResult.get("username"));

        // When & Then - Verify Charlie
        Map<String, String> charlieData = Map.of("username", "charlie", "password", "pass3");
        Map<String, String> charlieResult = provider.verify(charlieData);
        assertEquals("charlie", charlieResult.get("username"));
    }

    @Test
    void emptyPasswordIsRejected() {
        // Given
        User user = new User("alice", "secret");
        provider.setUsers(List.of(user));

        Map<String, String> postData = Map.of(
                "username", "alice",
                "password", ""
        );

        // When & Then
        assertThrows(NoSuchElementException.class, () -> provider.verify(postData));
    }

    @Test
    void nullPasswordInUser() {
        // Given
        User user = new User();
        user.setUsername("alice");
        // password is null
        provider.setUsers(List.of(user));

        Map<String, String> postData = Map.of(
                "username", "alice",
                "password", "anypassword"
        );

        // When & Then
        assertThrows(NoSuchElementException.class, () -> provider.verify(postData));
    }

    @Test
    void test() {
        assertTrue(isHashedPassword("$5$9d3c06e19528aebb$cZBA3E3SdoUvk865.WyPA5iNUEA7uwDlDX7D5Npkh8/"));
        assertTrue(isHashedPassword("$5$99a6391616158b48$PqFPn9f/ojYdRcu.TVsdKeeRHKwbWApdEypn6wlUQn5"));
    }
}
