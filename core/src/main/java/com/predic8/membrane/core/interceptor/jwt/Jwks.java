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
import com.predic8.membrane.core.util.text.TextUtil;
import org.jetbrains.annotations.NotNull;
import org.jose4j.jwk.RsaJsonWebKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import static com.predic8.membrane.core.interceptor.jwt.JwtSignInterceptor.DEFAULT_PKEY;
import static java.util.Collections.emptyList;

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
    private volatile HashMap<String, RsaJsonWebKey> keysByKid = new HashMap<>();

    String jwksUris;
    AuthorizationService authorizationService;
    private Router router;

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
            router.getTimerManager().schedulePeriodicTask(new TimerTask() {
                                                              @Override
                                                              public void run() {
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
                                                              }
                                                          }, authorizationService.getJwksRefreshInterval() * 1_000L, "JWKS Refresh"
            );
        }
    }

    public List<Jwk> getJwks() {
        return jwks;
    }

    @MCChildElement
    public Jwks setJwks(List<Jwk> jwks) {
        this.jwks = jwks;  // unnecessary, mainly for consistency when debugging
        if (router != null)  // set in init, so we can't update prior to that call
            this.keysByKid = buildKeyMap(jwks);
        return this;
    }

    public String getJwksUris() {
        return jwksUris;
    }

    @MCAttribute
    public Jwks setJwksUris(String jwksUris) {
        this.jwksUris = jwksUris;
        return this;
    }

    public HashMap<String, RsaJsonWebKey> getKeysByKid() {
        return keysByKid;
    }

    private HashMap<String, RsaJsonWebKey> buildKeyMap(List<Jwk> jwks) {
        var keyMap = jwks.stream()
                .map(this::extractRsaJsonWebKey)
                .collect(
                        () -> new HashMap<String, RsaJsonWebKey>(),
                        (m,e) -> m.put(e.getKeyId(),e),
                        HashMap::putAll
                );
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
        return Arrays.stream(jwksUris.split(" "))
                .map(uri -> parseJwksUriIntoList(router.getResolverMap(), router.getConfiguration().getBaseLocation(), mapper, uri, suppressExceptions))
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
            } else {
                throw new ConfigurationException(message, e);
            }
        }
    }

    private record JwkListByUri(String uri, List<?> jwks) {}

    private JwkListByUri parseJwksUriIntoList(ResolverMap resolverMap, String baseLocation, ObjectMapper mapper, String uri, boolean suppressExceptions) {
        try {
            InputStream resolve = authorizationService != null ?
                    authorizationService.resolve(resolverMap, baseLocation, uri) :
                    resolverMap.resolve(ResolverMap.combine(baseLocation, uri));
            return new JwkListByUri(uri, mapper.convertValue(mapper.readTree(resolve).path("keys"), List.class));
        } catch (JsonProcessingException e) {
            String message = "Could not parse JWK keys retrieved from %s.".formatted(uri);
            if (suppressExceptions) {
                log.error(message);
            } else {
                throw new ConfigurationException(message, e);
            }
        } catch (ResourceRetrievalException e) {
            String message = "Could not retrieve JWK keys from %s.".formatted(uri);
            if (suppressExceptions) {
                log.error(message);
            } else {
                throw new ConfigurationException(message, e);
            }
        } catch (Exception e) {
            if (suppressExceptions) {
                log.error(e.toString());
                log.error(e.getMessage());
            } else {
                throw new RuntimeException(e);
            }
        }
        return new JwkListByUri(uri, emptyList());
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
        public Jwk setKid(String kid) {
            this.kid = kid;
            return this;
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
                HTTPSchemaResolver httpSR = new HTTPSchemaResolver(router.getHttpClientFactory());
                httpSR.setHttpClientConfig(httpClientConfig);

                rm = rm.clone();
                rm.addSchemaResolver(httpSR);
            }

            String maybeJwk = get(rm, router.getConfiguration().getBaseLocation());

            Map<String,Object> mapped = mapper.readValue(maybeJwk, new TypeReference<>() {});

            if (mapped.containsKey("keys"))
                return handleJwks(mapper, mapped);

            return maybeJwk;
        }

        private String handleJwks(ObjectMapper mapper, Map<String, Object> mapped) {
            return ((List<Map>) mapped.get("keys")).stream()
                    .filter(m -> m.get("kid").toString().equals(kid))
                    .map(m -> {
                        try {
                            return mapper.writeValueAsString(m);
                        } catch (JsonProcessingException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .findFirst().orElseThrow();
        }
    }

    String getLongDescription() {
        if (jwksUris != null)
            return " JWKs from <a href=\" "+ TextUtil.toEnglishList("and", jwksUris.split(" ")) +"\">here</a> ";
        return "a predefined set of JWKs";
    }

}
