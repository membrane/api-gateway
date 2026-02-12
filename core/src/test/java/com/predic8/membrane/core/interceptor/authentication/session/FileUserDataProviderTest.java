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

package com.predic8.membrane.core.interceptor.authentication.session;

import com.predic8.membrane.core.router.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;

import static com.predic8.membrane.core.util.SecurityUtils.*;
import static org.junit.jupiter.api.Assertions.*;

class FileUserDataProviderTest {

    // Test constants
    private static final String TEST_PASSWORD_ALICE = "secret123";
    private static final String TEST_PASSWORD_BOB = "password456";
    @TempDir
    Path tempDir;
    private FileUserDataProvider provider;
    private Router router;

    @BeforeEach
    void setUp() {
        provider = new FileUserDataProvider();
        router = new DummyTestRouter();
    }

    /**
     * Helper method to create a test htpasswd file with hashed passwords
     */
    private Path createHtpasswdFile(String filename, Map<String, String> users) throws IOException {
        Path htpasswdFile = tempDir.resolve(filename);
        StringBuilder content = new StringBuilder();

        for (Map.Entry<String, String> entry : users.entrySet()) {
            String username = entry.getKey();
            String plainPassword = entry.getValue();

            // Generate SHA-512 hash (supported by Apache Commons Codec)
            String salt = "saltsalt"; // 8 characters minimum for SHA-512
            String hash = createPasswdCompatibleHash(new AlgoSalt("6", salt), plainPassword);

            content.append(username).append(":").append(hash).append("\n");
        }

        Files.writeString(htpasswdFile, content.toString());
        return htpasswdFile;
    }

    @Test
    void testInitWithValidHtpasswdFile() throws IOException {
        Map<String, String> testUsers = new HashMap<>();
        testUsers.put("alice", TEST_PASSWORD_ALICE);
        testUsers.put("bob", TEST_PASSWORD_BOB);

        Path htpasswdFile = createHtpasswdFile("test.htpasswd", testUsers);

        provider.setLocation(htpasswdFile.toString());
        provider.init(router);

        Map<String, User> users = provider.getUsersByName();
        assertEquals(2, users.size());
        assertTrue(users.containsKey("alice"));
        assertTrue(users.containsKey("bob"));
    }

    @Test
    void testInitWithSHA512Hash() throws IOException {
        // Using SHA-512 hash format: $6$salt$hash
        Map<String, String> testUsers = Map.of("testuser", "test");
        Path htpasswdFile = createHtpasswdFile("sha512.htpasswd", testUsers);

        provider.setLocation(htpasswdFile.toString());
        provider.init(router);

        Map<String, User> users = provider.getUsersByName();
        assertEquals(1, users.size());
        assertEquals("testuser", users.get("testuser").getUsername());
    }

    @Test
    void testInitWithEmptyFile() throws IOException {
        Path htpasswdFile = tempDir.resolve("empty.htpasswd");
        Files.writeString(htpasswdFile, "");

        provider.setLocation(htpasswdFile.toString());
        provider.init(router);

        assertTrue(provider.getUsersByName().isEmpty());
    }

    @Test
    void testInitWithMalformedLines() throws IOException {
        // Create one valid entry programmatically
        Map<String, String> validUsers = Map.of("validuser", "validpass");
        Path htpasswdFile = createHtpasswdFile("malformed.htpasswd", validUsers);

        // Append malformed lines
        Files.writeString(htpasswdFile,
                "malformed_line_without_colon\n" +
                ":nousername\n",
                StandardOpenOption.APPEND
        );

        provider.setLocation(htpasswdFile.toString());
        provider.init(router);

        Map<String, User> users = provider.getUsersByName();
        assertTrue(users.containsKey("validuser"));
        assertFalse(users.containsKey("malformed_line_without_colon"));
    }

    @Test
    void testInitWithNonExistentFile() {
        Path missing = tempDir.resolve("missing.htpasswd");
        provider.setLocation(missing.toString());
        assertThrows(RuntimeException.class, () -> provider.init(router));
    }

    @Test
    void testVerifyValidCredentials() throws IOException {
        Map<String, String> testUsers = Map.of("alice", TEST_PASSWORD_ALICE);
        Path htpasswdFile = createHtpasswdFile("verify.htpasswd", testUsers);

        provider.setLocation(htpasswdFile.toString());
        provider.init(router);

        Map<String, String> postData = new HashMap<>();
        postData.put("username", "alice");
        postData.put("password", TEST_PASSWORD_ALICE);

        Map<String, String> result = provider.verify(postData);
        assertNotNull(result);
        assertEquals("alice", result.get("username"));
    }

    @Test
    void testVerifyInvalidPassword() throws IOException {
        Map<String, String> testUsers = Map.of("alice", TEST_PASSWORD_ALICE);
        Path htpasswdFile = createHtpasswdFile("verify.htpasswd", testUsers);

        provider.setLocation(htpasswdFile.toString());
        provider.init(router);

        Map<String, String> postData = new HashMap<>();
        postData.put("username", "alice");
        postData.put("password", "wrongpassword");

        assertThrows(NoSuchElementException.class, () -> provider.verify(postData));
    }

    @Test
    void testVerifyMissingUsername() throws IOException {
        Map<String, String> testUsers = Map.of("alice", TEST_PASSWORD_ALICE);
        Path htpasswdFile = createHtpasswdFile("verify.htpasswd", testUsers);

        provider.setLocation(htpasswdFile.toString());
        provider.init(router);

        Map<String, String> postData = new HashMap<>();
        postData.put("password", TEST_PASSWORD_ALICE);

        assertThrows(NoSuchElementException.class, () -> provider.verify(postData));
    }

    @Test
    void testVerifyNullUsername() throws IOException {
        Map<String, String> testUsers = Map.of("alice", TEST_PASSWORD_ALICE);
        Path htpasswdFile = createHtpasswdFile("verify.htpasswd", testUsers);

        provider.setLocation(htpasswdFile.toString());
        provider.init(router);

        Map<String, String> postData = new HashMap<>();
        postData.put("username", null);
        postData.put("password", TEST_PASSWORD_ALICE);

        assertThrows(NoSuchElementException.class, () -> provider.verify(postData));
    }

    @Test
    void testVerifyNonExistentUser() throws IOException {
        Map<String, String> testUsers = Map.of("alice", TEST_PASSWORD_ALICE);
        Path htpasswdFile = createHtpasswdFile("verify.htpasswd", testUsers);

        provider.setLocation(htpasswdFile.toString());
        provider.init(router);

        Map<String, String> postData = new HashMap<>();
        postData.put("username", "nonexistent");
        postData.put("password", "anypassword");

        assertThrows(NoSuchElementException.class, () -> provider.verify(postData));
    }

    @Test
    void testUserAttributesAreReturned() throws IOException {
        Map<String, String> testUsers = Map.of("alice", TEST_PASSWORD_ALICE);
        Path htpasswdFile = createHtpasswdFile("verify.htpasswd", testUsers);

        provider.setLocation(htpasswdFile.toString());
        provider.init(router);

        var postData = new HashMap<String, String>();
        postData.put("username", "alice");
        postData.put("password", TEST_PASSWORD_ALICE);

        Map<String, String> attributes = provider.verify(postData);
        assertTrue(attributes.containsKey("username"));
    }

    @Test
    void testMultipleUsers() throws IOException {
        Map<String, String> testUsers = new HashMap<>();
        testUsers.put("alice", TEST_PASSWORD_ALICE);
        testUsers.put("bob", TEST_PASSWORD_BOB);
        testUsers.put("charlie", "charlie123");

        Path htpasswdFile = createHtpasswdFile("multi.htpasswd", testUsers);

        provider.setLocation(htpasswdFile.toString());
        provider.init(router);

        assertEquals(3, provider.getUsersByName().size());
        assertTrue(provider.getUsersByName().containsKey("alice"));
        assertTrue(provider.getUsersByName().containsKey("bob"));
        assertTrue(provider.getUsersByName().containsKey("charlie"));
    }

    @Test
    void testgetLocation() {
        String testPath = "/test/path/htpasswd";
        provider.setLocation(testPath);
        assertEquals(testPath, provider.getLocation());
    }

    @Test
    void passwordWithColons() throws IOException {
        // Manually create entry with colon in hash (tests current split behavior)
        Path htpasswdFile = tempDir.resolve("colon.htpasswd");
        Files.writeString(htpasswdFile, "user:$6$salt$hash:with:colons\n");

        provider.setLocation(htpasswdFile.toString());
        provider.init(router);

        var user = provider.getUsersByName().get("user");
        assertNotNull(user);
        // Verifies that the split limit preserves colons in the hash.
        assertEquals("$6$salt$hash:with:colons", user.getPassword());
    }

    @Test
    void testEmptyUsername() throws IOException {
        Path htpasswdFile = tempDir.resolve("empty.htpasswd");
        Files.writeString(htpasswdFile, ":$6$saltsalt$somehash\n");

        provider.setLocation(htpasswdFile.toString());
        provider.init(router);

        assertTrue(provider.getUsersByName().containsKey(""));
    }

    @Test
    void testWhitespaceInUsernameNotTrimmed() throws IOException {
        Path htpasswdFile = tempDir.resolve("whitespace.htpasswd");
        Files.writeString(htpasswdFile, " alice :$6$saltsalt$somehash\n");

        provider.setLocation(htpasswdFile.toString());
        provider.init(router);

        assertTrue(provider.getUsersByName().containsKey(" alice "));
        assertFalse(provider.getUsersByName().containsKey("alice"));
    }

    @Nested
    class UserTest {

        @Test
        void testUserCreation() {
            var user = new User("testuser", "testhash");
            assertEquals("testuser", user.getUsername());
            assertEquals("testhash", user.getPassword());
        }

        @Test
        void testUserAttributes() {
            var user = new User("testuser", "testhash");

            Map<String, String> attrs = user.getAttributes();
            assertEquals("testuser", attrs.get("username"));
            assertEquals("testhash", attrs.get("password"));
        }

        @Test
        void testSetAdditionalAttributes() {
            var user = new User("testuser", "testhash");

            Map<String, String> additionalAttrs = new HashMap<>();
            additionalAttrs.put("email", "test@example.com");
            additionalAttrs.put("role", "admin");

            user.setAttributes(additionalAttrs);

            assertEquals("test@example.com", user.getAttributes().get("email"));
            assertEquals("admin", user.getAttributes().get("role"));
            assertEquals("testuser", user.getAttributes().get("username"));
        }

        @Test
        void testSetUsername() {
            var user = new User("original", "hash");
            user.setUsername("newusername");
            assertEquals("newusername", user.getUsername());
        }

        @Test
        void testSetPassword() {
            var user = new User("user", "originalhash");
            user.setPassword("newhash");
            assertEquals("newhash", user.getPassword());
        }
    }
}