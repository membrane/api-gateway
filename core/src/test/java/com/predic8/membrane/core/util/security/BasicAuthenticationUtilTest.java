package com.predic8.membrane.core.util.security;

import com.predic8.membrane.core.exchange.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import java.net.*;
import java.nio.charset.*;
import java.util.*;

import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.Request.*;
import static org.junit.jupiter.api.Assertions.*;

class BasicAuthenticationUtilTest {


    private String encodeBasicAuth(String username, String password) {
        var credentials = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    @Nested
    class GetCredentialsTests {

        @Test
        void validCredentials() {
            var exc = createExchange(encodeBasicAuth("alice", "secret123"));

            var credentials = BasicAuthenticationUtil.getCredentials(exc);

            assertEquals("alice", credentials.username());
            assertEquals("secret123", credentials.password());
        }

        @Test
        void emptyPassword() {
            var exc = createExchange(encodeBasicAuth("user", ""));

            var credentials = BasicAuthenticationUtil.getCredentials(exc);

            assertEquals("user", credentials.username());
            assertEquals("", credentials.password());
        }

        @Test
        void emptyUsername() {
            var exc = createExchange(encodeBasicAuth("", "password"));

            var credentials = BasicAuthenticationUtil.getCredentials(exc);

            assertEquals("", credentials.username());
            assertEquals("password", credentials.password());
        }

        @Test
        void passwordContainingColons() {
            var exc = createExchange(encodeBasicAuth("user", "pass:with:colons"));

            var credentials = BasicAuthenticationUtil.getCredentials(exc);

            assertEquals("user", credentials.username());
            assertEquals("pass:with:colons", credentials.password());
        }

        @Test
        void multipleColons() {
            var exc = createExchange(encodeBasicAuth("admin", "p:a:s:s:w:o:r:d"));

            var credentials = BasicAuthenticationUtil.getCredentials(exc);

            assertEquals("admin", credentials.username());
            assertEquals("p:a:s:s:w:o:r:d", credentials.password());
        }

        @Test
        void spacesInCredentials() {
            var exc = createExchange(encodeBasicAuth("user name", "pass word"));

            var credentials = BasicAuthenticationUtil.getCredentials(exc);

            assertEquals("user name", credentials.username());
            assertEquals("pass word", credentials.password());
        }

        @Test
        void specialCharacters() {
            var exc = createExchange(encodeBasicAuth("user@domain.com", "p@ss!#$%"));

            var credentials = BasicAuthenticationUtil.getCredentials(exc);

            assertEquals("user@domain.com", credentials.username());
            assertEquals("p@ss!#$%", credentials.password());
        }

        @Test
        void utf8Characters() {
            var exc = createExchange(encodeBasicAuth("üser", "pässwörd"));

            var credentials = BasicAuthenticationUtil.getCredentials(exc);

            assertEquals("üser", credentials.username());
            assertEquals("pässwörd", credentials.password());
        }

        @Test
        void chineseCharacters() {
            var exc = createExchange(encodeBasicAuth("用户", "密码"));

            var credentials = BasicAuthenticationUtil.getCredentials(exc);

            assertEquals("用户", credentials.username());
            assertEquals("密码", credentials.password());
        }

        @Test
        void emoji() {
            var exc = createExchange(encodeBasicAuth("user", "pass🔒"));

            var credentials = BasicAuthenticationUtil.getCredentials(exc);

            assertEquals("user", credentials.username());
            assertEquals("pass🔒", credentials.password());
        }

        @Test
        void longPassword() {
            var longPassword = "a".repeat(1000);
            var exc = createExchange(encodeBasicAuth("user", longPassword));

            var credentials = BasicAuthenticationUtil.getCredentials(exc);

            assertEquals("user", credentials.username());
            assertEquals(longPassword, credentials.password());
        }

        @Test
        void extraWhitespace() {
            var credentials = "user:pass";
            var encoded = "Basic   " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)) + "  ";
            var exc = createExchange(encoded);

            var result = BasicAuthenticationUtil.getCredentials(exc);

            assertEquals("user", result.username());
            assertEquals("pass", result.password());
        }

        @Test
        void caseInsensitiveBasic() {
            var credentials = "user:pass";
            var encoded = "basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
            var exc = createExchange(encoded);

            var result = BasicAuthenticationUtil.getCredentials(exc);

            assertEquals("user", result.username());
            assertEquals("pass", result.password());
        }

        @Test
        void caseVariations() {
            var credentials = "user:pass";
            String[] prefixes = {"Basic ", "basic ", "BASIC ", "BaSiC ", "bAsIc "};

            for (var prefix : prefixes) {
                var encoded = prefix + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
                var exc = createExchange(encoded);

                var result = BasicAuthenticationUtil.getCredentials(exc);

                assertEquals("user", result.username());
                assertEquals("pass", result.password());
            }
        }

        @Test
        void realWorldExample() {
            // Real example: "admin:secret" -> "YWRtaW46c2VjcmV0"
            var exc = createExchange("Basic YWRtaW46c2VjcmV0");

            var credentials = BasicAuthenticationUtil.getCredentials(exc);

            assertEquals("admin", credentials.username());
            assertEquals("secret", credentials.password());
        }
    }

    @Nested
    class InvalidHeaderTests {

        @Test
        void nullHeader() {
            var exception = assertThrows(IllegalArgumentException.class, () ->
                    BasicAuthenticationUtil.getCredentials(createExchange(null))
            );
            assertEquals("Authorization header is required", exception.getMessage());
        }

        @Test
        void emptyHeader() {
            var exception = assertThrows(IllegalArgumentException.class, () ->
                    BasicAuthenticationUtil.getCredentials(createExchange(""))
            );
            assertEquals("Authorization header is required", exception.getMessage());
        }

        @Test
        void onlyBasicKeyword() {
            var exception = assertThrows(IllegalArgumentException.class, () ->
                    BasicAuthenticationUtil.getCredentials(createExchange("Basic"))
            );
            assertEquals("Not a Basic authentication header", exception.getMessage());
        }

        @Test
        void basicWithSingleSpace() {
            var exception = assertThrows(IllegalArgumentException.class, () ->
                    BasicAuthenticationUtil.getCredentials(createExchange("Basic "))
            );
            assertEquals("Missing credentials in Basic authentication header", exception.getMessage());
        }

        @Test
        void basicWithMultipleSpaces() {
            var exception = assertThrows(IllegalArgumentException.class, () ->
                    BasicAuthenticationUtil.getCredentials(createExchange("Basic    "))
            );
            assertEquals("Empty credentials in Basic authentication header", exception.getMessage());
        }

        @Test
        void bearerToken() {
            var exception = assertThrows(IllegalArgumentException.class, () ->
                    BasicAuthenticationUtil.getCredentials(createExchange("Bearer token"))
            );
            assertEquals("Not a Basic authentication header", exception.getMessage());
        }

        @Test
        void invalidBase64() {
            var exception = assertThrows(IllegalArgumentException.class, () ->
                    BasicAuthenticationUtil.getCredentials(createExchange("Basic not-valid!!!"))
            );
            assertEquals("Invalid Base64 encoding in Basic authentication header", exception.getMessage());
            assertNotNull(exception.getCause());
        }

        @Test
        void withoutColon() {
            var credentials = "usernameonly";
            var encoded = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
            var exception = assertThrows(IllegalArgumentException.class, () ->
                    BasicAuthenticationUtil.getCredentials(createExchange(encoded))
            );
            assertEquals("Invalid credentials format: missing colon separator", exception.getMessage());
        }
    }

    @Nested
    class BasicCredentialsRecordTests {

        @Test
        void creation() {
            var credentials = new BasicAuthenticationUtil.BasicCredentials("alice", "secret");

            assertEquals("alice", credentials.username());
            assertEquals("secret", credentials.password());
        }

        @Test
        void emptyValues() {
            var credentials = new BasicAuthenticationUtil.BasicCredentials("", "");

            assertEquals("", credentials.username());
            assertEquals("", credentials.password());
        }

        @Test
        void nullUsernameThrows() {
            var exception = assertThrows(NullPointerException.class, () ->
                    new BasicAuthenticationUtil.BasicCredentials(null, "pass")
            );

            assertEquals("Username cannot be null", exception.getMessage());
        }

        @Test
        void nullPasswordThrows() {
            var exception = assertThrows(NullPointerException.class, () ->
                    new BasicAuthenticationUtil.BasicCredentials("user", null)
            );

            assertEquals("Password cannot be null", exception.getMessage());
        }

        @Test
        void equality() {
            var cred1 = new BasicAuthenticationUtil.BasicCredentials("user", "pass");
            var cred2 = new BasicAuthenticationUtil.BasicCredentials("user", "pass");
            var cred3 = new BasicAuthenticationUtil.BasicCredentials("other", "pass");
            var cred4 = new BasicAuthenticationUtil.BasicCredentials("user", "different");

            assertEquals(cred1, cred2);
            assertNotEquals(cred1, cred3);
            assertNotEquals(cred1, cred4);
            assertEquals(cred1.hashCode(), cred2.hashCode());
        }

        @Test
        void toStringContainsValues() {
            var credentials = new BasicAuthenticationUtil.BasicCredentials("alice", "secret");

            var toString = credentials.toString();

            assertTrue(toString.contains("alice"));
            assertTrue(toString.contains("secret"));
        }

        @Test
        void toMap() {
            var credentials = new BasicAuthenticationUtil.BasicCredentials("alice", "secret123");

            var map = credentials.toMap();

            assertEquals(2, map.size());
            assertEquals("alice", map.get("username"));
            assertEquals("secret123", map.get("password"));
        }

        @Test
        void toMapWithEmptyValues() {
            var credentials = new BasicAuthenticationUtil.BasicCredentials("", "");

            var map = credentials.toMap();

            assertEquals("", map.get("username"));
            assertEquals("", map.get("password"));
        }

        @Test
        void toMapWithSpecialCharacters() {
            var credentials = new BasicAuthenticationUtil.BasicCredentials("user@domain.com", "p@ss:w0rd!");

            var map = credentials.toMap();

            assertEquals("user@domain.com", map.get("username"));
            assertEquals("p@ss:w0rd!", map.get("password"));
        }

        @Test
        void toMapIsNewInstance() {
            var credentials = new BasicAuthenticationUtil.BasicCredentials("user", "pass");

            var map1 = credentials.toMap();
            var map2 = credentials.toMap();

            assertNotSame(map1, map2);
            assertEquals(map1, map2);
        }

        @Test
        void toMapCanBeModified() {
            var credentials = new BasicAuthenticationUtil.BasicCredentials("user", "pass");

            var map = credentials.toMap();
            map.put("extra", "value");

            assertEquals(3, map.size());
            assertEquals("value", map.get("extra"));
        }
    }

    @ParameterizedTest
    @CsvSource({
            "alice, password123",
            "user@domain.com, p@ss!",
            "admin, pass:with:colons",
            "test, ''",
            "'', password",
            "user name, pass word",
            "üser, pässwörd",
            "用户, 密码",
            "test, 'abc:def:ghi'",
            "user123, 'P@$$w0rd!'"
    })
    void variousCredentialFormats(String username, String password) {
        var exc = createExchange(encodeBasicAuth(username, password));

        var credentials = BasicAuthenticationUtil.getCredentials(exc);

        assertEquals(username, credentials.username());
        assertEquals(password, credentials.password());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Bearer token123",
            "Digest realm=\"test\"",
            "OAuth token",
            "AWS4-HMAC-SHA256",
            "Just some text",
            "NotBasic dXNlcjpwYXNz"
    })
    void nonBasicAuthSchemes(String authHeader) {
        var exc = createExchange(authHeader);

        assertThrows(IllegalArgumentException.class, () ->
                BasicAuthenticationUtil.getCredentials(exc)
        );
    }

    @Test
    void integrationWithUserDataProvider() {
        // Simulate typical usage pattern
        var exc = createExchange(encodeBasicAuth("alice", "secret123"));

        var credentials = BasicAuthenticationUtil.getCredentials(exc);
        var postData = credentials.toMap();

        // This map can be passed to UserDataProvider.verify()
        assertNotNull(postData);
        assertEquals("alice", postData.get("username"));
        assertEquals("secret123", postData.get("password"));
    }

    @Test
    void consecutiveCallsReturnSameValues() {
        var exc = createExchange(encodeBasicAuth("user", "pass"));

        var credentials1 = BasicAuthenticationUtil.getCredentials(exc);
        var credentials2 = BasicAuthenticationUtil.getCredentials(exc);

        assertEquals(credentials1, credentials2);
    }

    private Exchange createExchange(String headerValue) {
        try {
            return get("/foo").header(AUTHORIZATION, headerValue).buildExchange();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}