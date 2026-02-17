package com.predic8.membrane.core.interceptor.jwt;

import com.predic8.membrane.core.router.DefaultRouter;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.oauth2.authorizationservice.AuthorizationService;
import com.predic8.membrane.core.proxies.ServiceProxy;
import com.predic8.membrane.core.proxies.ServiceProxyKey;
import com.predic8.membrane.core.transport.http.HttpClient;
import org.jetbrains.annotations.NotNull;
import org.jose4j.jwk.JsonWebKeySet;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.jwk.RsaJwkGenerator;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.lang.JoseException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static com.predic8.membrane.core.interceptor.jwt.JwtAuthInterceptorTest.KID;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class JwksRefreshTest {

    public static final int PROVIDER_PORT = 3000;
    public static final int AUTH_INTERCEPTOR_PORT = 3001;
    static DefaultRouter jwksProvider;
    static DefaultRouter jwtValidator;

    static RsaJsonWebKey privateKey1;
    static RsaJsonWebKey publicKey1;
    static RsaJsonWebKey privateKey2;
    static RsaJsonWebKey publicKey2;

    static final AtomicReference<JsonWebKeySet> currentJwkSet = new AtomicReference<>();

    @BeforeAll
    public static void setup() throws Exception {
        privateKey1 = RsaJwkGenerator.generateJwk(2048);
        privateKey1.setKeyId(KID);
        publicKey1 = new RsaJsonWebKey(privateKey1.getRsaPublicKey());
        publicKey1.setKeyId(KID);

        privateKey2 = RsaJwkGenerator.generateJwk(2048);
        privateKey2.setKeyId(KID + "2");
        publicKey2 = new RsaJsonWebKey(privateKey2.getRsaPublicKey());
        publicKey2.setKeyId(KID + "2");

        currentJwkSet.set(new JsonWebKeySet(publicKey1));

        jwksProvider = new DefaultRouter();
        jwksProvider.add(proxyWithInterceptors(PROVIDER_PORT, jwkServingInterceptor(currentJwkSet::get)));
        jwksProvider.start();

        // Wait for jwksProvider to start
        Thread.sleep(1000);

        jwtValidator = new DefaultRouter();
        jwtValidator.add(proxyWithInterceptors(
                AUTH_INTERCEPTOR_PORT,
                jwtAuthInterceptor(),
                new AbstractInterceptor() {
                    @Override
                    public Outcome handleRequest(Exchange exc) {
                        exc.setResponse(Response.ok().build());
                        return Outcome.RETURN;
                    }
                })
        );
        jwtValidator.start();
    }

    private static @NotNull ServiceProxy proxyWithInterceptors(int port, @NotNull AbstractInterceptor... interceptors) {
        var proxy = new ServiceProxy(new ServiceProxyKey(port), null, 0);
        Arrays.stream(interceptors).forEach(proxy.getFlow()::add);
        return proxy;
    }

    private static @NotNull JwtAuthInterceptor jwtAuthInterceptor() {
        Jwks jwks = new Jwks();
        jwks.setJwksUris("http://localhost:%d/jwks".formatted(PROVIDER_PORT));
        jwks.setAuthorizationService(buildAuthorizationService(1));

        JwtAuthInterceptor jwtAuth = new JwtAuthInterceptor();
        jwtAuth.setExpectedAud("some-audience");
        jwtAuth.setJwks(jwks);

        return jwtAuth;
    }

    private static @NotNull AbstractInterceptor jwkServingInterceptor(final Supplier<JsonWebKeySet> jwkSupplier) {
        return new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) {
                exc.setResponse(Response.ok(jwkSupplier.get().toJson()).contentType("application/json").build());
                return Outcome.RETURN;
            }
        };
    }

    private static @NotNull AuthorizationService buildAuthorizationService(int jwksRefreshInterval) {
        AuthorizationService authService = new AuthorizationService() {
            @Override public void init() {}
            @Override public String getIssuer() { return null; }
            @Override public String getJwksEndpoint() { return null; }
            @Override public String getEndSessionEndpoint() { return null; }
            @Override public String getLoginURL(String callbackURL) { return null; }
            @Override public String getUserInfoEndpoint() { return null; }
            @Override public String getSubject() { return null; }
            @Override protected String getTokenEndpoint() { return null; }
            @Override public String getRevocationEndpoint() { return null; }
        };
        authService.setJwksRefreshInterval(jwksRefreshInterval);
        authService.setHttpClient(new HttpClient());
        return authService;
    }

    @AfterAll
    public static void teardown() throws IOException {
        jwksProvider.stop();
        jwtValidator.stop();
    }

    @Test
    public void testRefresh() throws Exception {
        String authInterceptorUrl = "http://localhost:%d/".formatted(AUTH_INTERCEPTOR_PORT);

        try (HttpClient hc = new HttpClient()) {

            // 1. initial key works
            Exchange exc1 = new Request.Builder()
                    .get(authInterceptorUrl)
                    .header("Authorization", "Bearer " + createJwt(privateKey1))
                    .buildExchange();
            hc.call(exc1);
            assertEquals(200, exc1.getResponse().getStatusCode());

            // 2. switch keys
            currentJwkSet.set(new JsonWebKeySet(publicKey2));
            Thread.sleep(2000); // wait for refresh

            // 3. new key works
            Exchange exc3 = new Request.Builder()
                    .get(authInterceptorUrl)
                    .header("Authorization", "Bearer " + createJwt(privateKey2))
                    .buildExchange();
            hc.call(exc3);
            assertEquals(200, exc3.getResponse().getStatusCode());

            // 4. old key does not work anymore
            Exchange exc2 = new Request.Builder()
                    .get(authInterceptorUrl)
                    .header("Authorization", "Bearer " + createJwt(privateKey1))
                    .buildExchange();
            hc.call(exc2);
            assertEquals(400, exc2.getResponse().getStatusCode());
        }
    }

    private static String createJwt(RsaJsonWebKey privateKey) throws JoseException {
        JwtClaims claims = new JwtClaims();
        claims.setSubject("user");
        claims.setIssuer("some-issuer");
        claims.setAudience("some-audience");
        claims.setExpirationTimeMinutesInTheFuture(10);
        claims.setIssuedAtToNow();
        claims.setNotBeforeMinutesInThePast(2);

        JsonWebSignature jws = new JsonWebSignature();
        jws.setPayload(claims.toJson());
        jws.setKey(privateKey.getPrivateKey());
        jws.setKeyIdHeaderValue(privateKey.getKeyId());
        jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);

        return jws.getCompactSerialization();
    }
}
