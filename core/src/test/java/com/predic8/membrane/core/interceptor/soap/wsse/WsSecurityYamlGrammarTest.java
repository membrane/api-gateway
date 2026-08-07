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
package com.predic8.membrane.core.interceptor.soap.wsse;

import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.flow.RequestInterceptor;
import com.predic8.membrane.core.proxies.Proxy;
import com.predic8.membrane.core.router.DefaultRouter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.predic8.membrane.core.router.YamlRouterBootstrap.loadIntoRouter;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins down the configuration grammar of {@code wsSecurity}: that {@code validate} and
 * {@code secure} are two distinct, ordered lists, and that the same element name means a different
 * thing in each - a {@code signature} under {@code validate} verifies, one under {@code secure}
 * signs. Nothing else asserts that, because the two element sets are only kept apart by
 * {@code validate}/{@code secure} being separate declared types.
 */
class WsSecurityYamlGrammarTest {

    @TempDir
    Path tempDir;

    private static final String CONFIG = """
            api:
              port: 2000
              flow:
                - request:
                    - wsSecurity:
                        actor: http://example.com/gateway
                        mustUnderstand: false
                        keystore:
                          location: classpath:/alias-keystore.p12
                          keyAlias: key1
                          keyPassword: secret
                        truststore:
                          location: classpath:/alias-truststore.p12
                          password: secret
                        validate:
                          - usernameToken:
                              username: alice
                              password: secret
                          - signature:
                              clockSkew: PT1M
                              requiredReferences:
                                - by: BODY
                        secure:
                          - timestamp:
                              ttl: PT2M
                          - signature:
                              references:
                                - by: BODY
                                - by: TIMESTAMP
                                - xpath: //*[local-name()='order']
            """;

    private WsSecurityInterceptor parse(String config) throws Exception {
        Path file = tempDir.resolve("apis.yaml");
        Files.writeString(file, config);

        DefaultRouter router = new DefaultRouter();
        loadIntoRouter(router, file.toString());

        Proxy proxy = router.getRuleManager().getRules().getFirst();
        Interceptor request = proxy.getFlow().getFirst();
        assertInstanceOf(RequestInterceptor.class, request);
        return assertInstanceOf(WsSecurityInterceptor.class,
                ((RequestInterceptor) request).getFlow().getFirst());
    }

    @Test
    void validateAndSecureBindToTheirOwnElementSets() throws Exception {
        WsSecurityInterceptor wsSecurity = parse(CONFIG);

        assertEquals("http://example.com/gateway", wsSecurity.getActor());
        assertFalse(wsSecurity.isMustUnderstand());
        assertNotNull(wsSecurity.getKeyStore());
        assertNotNull(wsSecurity.getTrustStore());

        List<ValidatePart> validate = wsSecurity.getValidateParts();
        assertEquals(2, validate.size());
        assertInstanceOf(UsernameTokenValidatePart.class, validate.getFirst());
        assertEquals("alice", ((UsernameTokenValidatePart) validate.getFirst()).getUsername());
        SignatureValidatePart validation = assertInstanceOf(SignatureValidatePart.class, validate.getLast());
        assertEquals("PT1M", validation.getClockSkew());
        assertEquals(List.of(SignatureReference.By.BODY),
                validation.getRequiredReferences().stream().map(SignatureReference::getBy).toList());

        List<SecurePart> secure = wsSecurity.getSecureParts();
        assertEquals(2, secure.size());
        assertEquals("PT2M", assertInstanceOf(TimestampSecurePart.class, secure.getFirst()).getTtl());
        SignatureSecurePart signing = assertInstanceOf(SignatureSecurePart.class, secure.getLast());
        // An xpath reference infers by: XPATH rather than carrying it - this is the shape the
        // element's own @yaml reference example shows, so binding it here keeps that example honest.
        assertEquals(List.of(SignatureReference.By.BODY, SignatureReference.By.TIMESTAMP,
                        SignatureReference.By.XPATH),
                signing.getReferences().stream().map(SignatureReference::getBy).toList());
        assertEquals("//*[local-name()='order']", signing.getReferences().getLast().getXpath());
    }

    /**
     * {@code timestamp} only exists under {@code secure}: there is nothing to validate about a
     * timestamp on its own, freshness is checked by the {@code signature} that covers it.
     */
    @Test
    void timestampIsNotAValidatePart() {
        assertThrows(Exception.class, () -> parse("""
                api:
                  port: 2000
                  flow:
                    - request:
                        - wsSecurity:
                            validate:
                              - timestamp:
                                  ttl: PT2M
                """));
    }
}
