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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.config.security.Blob;
import com.predic8.membrane.core.resolver.ResolverMap;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@MCElement(name="jwks")
public class Jwks {

    List<Jwk> jwks;
    String jwksUris;

    public List<Jwk> getJwks() {
        return jwks;
    }

    @MCChildElement
    public Jwks setJwks(List<Jwk> jwks) {
        this.jwks = jwks;
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

    public void init(ResolverMap resolverMap, String baseLocation) {
        if(jwksUris == null || jwksUris.isEmpty())
            return;

        ObjectMapper mapper = new ObjectMapper();
        for (String uri : jwksUris.split(" ")) {
            try {
                for (Object jwkRaw : parseJwksUriIntoList(resolverMap, baseLocation, mapper, uri)) {
                    Jwk jwk = new Jwk();
                    jwk.setContent(mapper.writeValueAsString(jwkRaw));
                    this.jwks.add(jwk);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private List parseJwksUriIntoList(ResolverMap resolverMap, String baseLocation, ObjectMapper mapper, String uri) throws java.io.IOException {
        return (List) mapper.readValue(resolverMap.resolve(ResolverMap.combine(baseLocation, uri)), Map.class).get("keys");
    }

    @MCElement(name="jwk", mixed = true, topLevel = false, id="jwks-jwk")
    public static class Jwk extends Blob {

        String kid;

        public String getKid() {
            return kid;
        }

        @MCAttribute
        public Jwk setKid(String kid) {
            this.kid = kid;
            return this;
        }

        public String getJwk(ResolverMap resolverMap, String baseLocation, ObjectMapper mapper) throws IOException {
            String maybeJwk = get(resolverMap, baseLocation);

            Map<String,Object> mapped = mapper.readValue(maybeJwk,Map.class);

            if(mapped.containsKey("keys"))
                return handleJwks(mapper, mapped);

            return maybeJwk;
        }

        private String handleJwks(ObjectMapper mapper, Map<String, Object> mapped) {
            return ((List<Map>)mapped.get("keys")).stream()
                    .filter(m -> m.get("kid").toString().equals(kid))
                    .map(m -> {
                        try {
                            return mapper.writeValueAsString(m);
                        } catch (JsonProcessingException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .findFirst().get();
        }
    }
}
