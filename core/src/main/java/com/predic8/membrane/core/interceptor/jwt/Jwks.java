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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import static java.util.Collections.emptyList;

/**
 * @description
 * JSON Web Key Set, configured <b>either</b> by an explicit list of JWK <b>or</b> by a list of JWK URIs that will be refreshed periodically.
 */
@MCElement(name="jwks")
public class Jwks {

    private static final Logger log = LoggerFactory.getLogger(Jwks.class);
    volatile List<Jwk> jwks = new ArrayList<>();
    String jwksUris;
    AuthorizationService authorizationService;
    private final List<Runnable> observers = new ArrayList<>();

    public List<Jwk> getJwks() {
        return jwks;
    }

    @MCChildElement
    public Jwks setJwks(List<Jwk> jwks) {
        this.jwks = jwks;
        notifyObservers();
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

    public void init(Router router) {
        if(jwksUris == null || jwksUris.isEmpty())
            return;
        if (!jwks.isEmpty())
            throw new ConfigurationException("JWKs cannot be set both via JwksUris and Jwks elements.");
        this.jwks = loadJwks(router, false);
        if (authorizationService.getJwksRefreshInterval() > 0) {
            router.getTimerManager().schedulePeriodicTask(new TimerTask() {
                                                              @Override
                                                              public void run() {
                                                                  setJwks(loadJwks(router, true));
                                                              }
                                                          }, authorizationService.getJwksRefreshInterval() * 1_000L, "JWKS Refresh"
            );
        }
    }

    private List<Jwk> loadJwks(Router router, boolean suppressExceptions) {
        ObjectMapper mapper = new ObjectMapper();
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
            return new JwkListByUri(uri, ((List<?>) mapper.readValue(resolve, Map.class).get("keys")));
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

    public void addObserver(Runnable observer) {
        observers.add(observer);
    }

    private void notifyObservers() {
        for (Runnable observer : observers) {
            try {
                observer.run();
            } catch (Exception e) {
                log.error("Error notifying observer", e);
            }
        }
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
