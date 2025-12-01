/* Copyright 2023 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.jwt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.Base64;
import java.util.Map;

import static com.predic8.membrane.core.interceptor.jwt.JwtAuthInterceptor.*;
import static tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION;
import static tools.jackson.databind.DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY;

public class JsonWebToken {

    private static final Logger log = LoggerFactory.getLogger(JsonWebToken.class.getName());

    public static abstract class AbstractJwtSubHolder {
        private final Map<String, Object> data;
        protected AbstractJwtSubHolder(Map<String, Object> data) {
            this.data = data;
        }
        public Object get(String key) throws JWTException {
            if (data.containsKey(key)) {
                return data.get(key);
            }
            throw new JWTException(ERROR_JWT_VALUE_NOT_PRESENT(key), ERROR_JWT_VALUE_NOT_PRESENT_ID);
        }
    }

    public static class Header extends AbstractJwtSubHolder {
        protected Header(Map<String, Object> data) {
            super(data);
        }
        public String kid() throws JWTException {
            return get("kid").toString();
        }
    }

    public static class Payload extends AbstractJwtSubHolder {
        protected Payload(Map<String, Object> data) {
            super(data);
        }
    }

    private final Header header;
    private final Payload payload;

    private static Base64.Decoder decoder = Base64.getUrlDecoder();
    private static final ObjectMapper mapper = JsonMapper.builder()
            .enable(FAIL_ON_READING_DUP_TREE_KEY)
            .enable(STRICT_DUPLICATE_DETECTION)
            .build();

    public JsonWebToken(String jwt) throws JWTException {
        var chunks = jwt.split("\\.");

        if (chunks.length < 3) {
            log.warn("Less than 3 parts in JWT header: {}", jwt);
            throw new JWTException(ERROR_MALFORMED_COMPACT_SERIALIZATION, ERROR_MALFORMED_COMPACT_SERIALIZATION_ID);
        }

        try {
            this.header = new Header(mapper.readValue(decoder.decode(chunks[0]), Map.class));
            this.payload = new Payload(mapper.readValue(decoder.decode(chunks[1]), Map.class));
        } catch (JacksonException e) {
            throw new JWTException(ERROR_DECODED_HEADER_NOT_JSON, ERROR_DECODED_HEADER_NOT_JSON_ID);
        }
    }

    public Header getHeader() {
        return header;
    }

    public Payload getPayload() {
        return payload;
    }
}
