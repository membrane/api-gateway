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

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Base64;
import java.util.Map;

public class JsonWebToken {

    public static abstract class AbstractJwtSubHolder {
        private final Map<String, Object> data;
        protected AbstractJwtSubHolder(Map<String, Object> data) {
            this.data = data;
        }
        public Object get(String key) throws JWTException {
            if (data.containsKey(key)) {
                return data.get(key);
            }
            throw new JWTException(ERROR_JWT_VALUE_NOT_PRESENT(key));
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

    public static final String ERROR_MALFORMED_COMPACT_SERIALIZATION = "JWTs compact serialization not valid";
    public static final String ERROR_DECODED_HEADER_NOT_JSON = "JWT header is not valid JSON";

    public static String ERROR_JWT_VALUE_NOT_PRESENT(String key) {
        return "JWT does not contain '" + key + "'";
    }

    public JsonWebToken(String jwt) throws JWTException {
        var chunks = jwt.split("\\.");

        if (chunks.length < 3)
            throw new JWTException(ERROR_MALFORMED_COMPACT_SERIALIZATION);

        var decoder = Base64.getUrlDecoder();
        var mapper = new ObjectMapper();

        try {
            this.header = new Header(mapper.readValue(decoder.decode(chunks[0]), Map.class));
            this.payload = new Payload(mapper.readValue(decoder.decode(chunks[1]), Map.class));
        } catch (IOException e) {
            throw new JWTException(ERROR_DECODED_HEADER_NOT_JSON);
        }
    }

    public Header getHeader() {
        return header;
    }

    public Payload getPayload() {
        return payload;
    }
}
