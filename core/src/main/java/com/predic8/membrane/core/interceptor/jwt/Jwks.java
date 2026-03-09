/*
 * Copyright 2021 predic8 GmbH, www.predic8.com
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.interceptor.jwt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.config.security.Blob;
import com.predic8.membrane.core.interceptor.oauth2.authorizationservice.AuthorizationService;
import com.predic8.membrane.core.resolver.HTTPSchemaResolver;
import com.predic8.membrane.core.resolver.ResolverMap;
import com.predic8.membrane.core.resolver.ResourceRetrievalException;
import com.predic8.membrane.core.router.Router;
import com.predic8.membrane.core.transport.http.client.HttpClientConfiguration;
import com.predic8.membrane.core.util.ConfigurationException;
import com.predic8.membrane.core.util.StringList;
import com.predic8.membrane.core.util.text.TextUtil;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jose4j.jwk.RsaJsonWebKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import static com.predic8.membrane.core.interceptor.jwt.JwtSignInterceptor.DEFAULT_PKEY;
import static com.predic8.membrane.core.util.TimerTaskUtil.createTimerTask;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyList;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

/**
 * @description
 * JSON Web Key Set, configured <b>either</b> by an explicit list of JWK <b>or</b> by a list of JWK URIs that will be refreshed periodically.
 */
@MCElement(name="jwks")
public class Jwks {

    public static final String DEFAULT_JWK_WARNING = """
            \n------------------------------------ DEFAULT JWK IN USE! ------------------------------------
                    This key is for demonstration purposes only and UNSAFE for production use.          \s
            ---------------------------------------------------------------------------------------------""";
    private static final Logger log = LoggerFactory.getLogger(Jwks.class);
    final ObjectMapper mapper = new ObjectMapper();

    private volatile List<Jwk> jwks = new ArrayList<>(); // this is basically a write-only field, contents are converted to keysByKid ASAP
    private volatile Map<String, RsaJsonWebKey> keysByKid = new HashMap<>();

    List<String> jwksUris = emptyList();
    AuthorizationService authorizationService;
    private Router router;
    private final Runnable refreshJwksTask = () -> {
        try {
            List<Jwk> loaded = loadJwks(true);
            if (!loaded.isEmpty()) {
                setJwks(loaded);
            } else {
                log.warn("JWKS refresh returned no keys — keeping previous key set.");
            }
        } catch (Exception e) {
            log.error("JWKS refresh failed, will retry on next interval.", e);
        }
    };

    public void init(Router router) {
        this.router = router;
        if(jwksUris == null || jwksUris.isEmpty()) {
            if (jwks.isEmpty())
                throw new ConfigurationException("JWKs need to be configured either via JwksUris or Jwks.");
            this.keysByKid = buildKeyMap(jwks);
            return;
        }
        if (!jwks.isEmpty())
            throw new ConfigurationException("JWKs cannot be set both via JwksUris and Jwks elements.");
        setJwks(loadJwks(false));
        if (authorizationService != null && authorizationService.getJwksRefreshInterval() > 0) {
            router.getTimerManager().schedulePeriodicTask(createTimerTask(refreshJwksTask), authorizationService.getJwksRefreshInterval() * 1_000L, "JWKS Refresh");
        }
    }

    public List<Jwk> getJwks() {
        return jwks;
    }

    @MCChildElement
    public void setJwks(List<Jwk> jwks) {
        if (router != null) {  // set in init, so we can't update prior to that call
            if (jwks == null) throw new ConfigurationException("JWKs list must not be null.");
            this.keysByKid = buildKeyMap(jwks);
        }
        this.jwks = (jwks == null) ? emptyList() : jwks;  // unnecessary, mainly for consistency when debugging
    }

    public String getJwksUris() {
        return String.join(" ", jwksUris);
    }

    @MCAttribute
    public void setJwksUris(String jwksUris) {
        if (jwksUris == null) {
            this.jwksUris = emptyList();
        }
        this.jwksUris = StringList.parseToList(jwksUris);
    }

    public Optional<RsaJsonWebKey> getKeyByKid(String kid) {
        return Optional.ofNullable(keysByKid.get(kid));
    }

    private Map<String, RsaJsonWebKey> buildKeyMap(List<Jwk> jwks) {
        var keyMap = jwks.stream()
                .map(this::extractRsaJsonWebKey)
                .collect(toMap(RsaJsonWebKey::getKeyId, identity(),
                        (k1, k2) -> k2  // second entry wins
                ));
        if (keyMap.isEmpty())
            throw new RuntimeException("No JWKs given or none resolvable - please specify at least one resolvable JWK");
        return keyMap;
    }

    private @NotNull RsaJsonWebKey extractRsaJsonWebKey(Jwk jwk) {
        try {
            var params = mapper.readValue(jwk.getJwk(router, mapper), new TypeReference<Map<String, Object>>() {});
            if (Objects.equals(params.get("p"), DEFAULT_PKEY)) {
                log.warn(DEFAULT_JWK_WARNING);
                if (router.getConfiguration().isProduction()) {
                    throw new RuntimeException("Default JWK detected in production environment. Please use a secure key.");
                }
            }
            return new RsaJsonWebKey(params);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private List<Jwk> loadJwks(boolean suppressExceptions) {
        return jwksUris.stream()
                .map(uri -> parseJwksUriIntoList(uri, suppressExceptions))
                .flatMap(l -> l.jwks().stream().map(jwkRaw -> convertToJwk(jwkRaw, mapper, l.uri(), suppressExceptions)))
                .filter(Objects::nonNull)
                .toList();
    }

    private static Jwk convertToJwk(Object jwkRaw, ObjectMapper mapper, String uri, boolean suppressExceptions) {
        try {
            Jwk jwk = new Jwk();
            jwk.setContent(mapper.writeValueAsString(jwkRaw));
            return jwk;
        } catch (JsonProcessingException e) {
            String message = "Could not parse JWK keys retrieved from %s.".formatted(uri);
            if (suppressExceptions) {
                log.error(message);
                return null;
            }
            throw new ConfigurationException(message, e);
        }
    }

    private record JwkListByUri(String uri, List<Map<String, Object>> jwks) {}

    private JwkListByUri parseJwksUriIntoList(String uri, boolean suppressExceptions) {
        try (InputStream jwksResponse = resolveToken(uri)) {
            return new JwkListByUri(uri, extractKeys(new String(jwksResponse.readAllBytes(), UTF_8), mapper));
        } catch (Exception e) {
            String message = switch (e) {
                case JsonProcessingException ignored -> "Could not parse JWK keys retrieved from %s.".formatted(uri);
                case ResourceRetrievalException ignored -> "Could not retrieve JWK keys from %s.".formatted(uri);
                default -> {
                    if (suppressExceptions) log.error(e.toString());
                    yield e.getMessage();
                }
            };
            if (suppressExceptions) {
                log.error(message);
                return new JwkListByUri(uri, emptyList());
            }
            if (e instanceof JsonProcessingException || e instanceof ResourceRetrievalException) {
                throw new ConfigurationException(message, e);
            }
            throw new RuntimeException(message, e);
        }
    }

    private static List<Map<String, Object>> extractKeys(String jwksResponse, ObjectMapper mapper) throws IOException {
        return mapper.convertValue(mapper.readTree(jwksResponse).path("keys"), new TypeReference<>() {});
    }

    private InputStream resolveToken(String uri) throws Exception {
        var resolverMap = router.getResolverMap();
        var baseLocation = router.getConfiguration().getBaseLocation();
        return authorizationService != null ?
                authorizationService.resolve(resolverMap, baseLocation, uri) :
                resolverMap.resolve(ResolverMap.combine(baseLocation, uri));
    }

    public AuthorizationService getAuthorizationService() {
        return authorizationService;
    }

    @MCAttribute
    public void setAuthorizationService(AuthorizationService authService) {
        authorizationService = authService;
    }

    @MCElement(name="jwk", mixed = true, component = false, id="jwks-jwk")
    public static class Jwk extends Blob {

        String kid;

        private HttpClientConfiguration httpClientConfig;

        public String getKid() {
            return kid;
        }

        @MCAttribute
        public void setKid(String kid) {
            this.kid = kid;
        }

        /**
         * @description Sets the HTTP client configuration.
         *
         * @param httpClientConfig the configuration to set for the HTTP client
         */
        @MCAttribute
        public void setHttpClientConfig(HttpClientConfiguration httpClientConfig) {
            this.httpClientConfig = httpClientConfig;
        }

        public HttpClientConfiguration getHttpClientConfig() {
            return httpClientConfig;
        }

        public String getJwk(Router router, ObjectMapper mapper) throws IOException {
            ResolverMap rm = router.getResolverMap();

            if (httpClientConfig != null) {
                HTTPSchemaResolver httpSR = new HTTPSchemaResolver(router.getHttpClientFactory().createClient(httpClientConfig));

                rm = rm.clone();
                rm.addSchemaResolver(httpSR);
            }

            String maybeJwk = get(rm, router.getConfiguration().getBaseLocation());

            Map<String,Object> mapped = mapper.readValue(maybeJwk, new TypeReference<>() {});

            if (mapped.containsKey("keys"))
                return handleJwks(mapper, mapped);

            return maybeJwk;
        }

        private String handleJwks(ObjectMapper mapper, Map<String, Object> mapped) throws IOException {
            return extractKeys(mapper.writeValueAsString(mapped), mapper).stream()
                    .filter(m -> Objects.equals(m.get("kid"), kid))
                    .map(m -> {
                        try {
                            return mapper.writeValueAsString(m);
                        } catch (JsonProcessingException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .findFirst().orElseThrow(() -> new RuntimeException("No JWK found for kid '" + kid + "'."));
        }
    }

    String getLongDescription() {
        if (jwksUris != null && !jwksUris.isEmpty())
            return " JWKs from <a href=\" "+ TextUtil.toEnglishList("and", jwksUris.toArray(new String[0])) +"\">here</a> ";
        return "a predefined set of JWKs";
    }

}
