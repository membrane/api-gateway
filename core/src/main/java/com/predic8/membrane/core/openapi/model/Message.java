/*
 *  Copyright 2022 predic8 GmbH, www.predic8.com
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.openapi.model;

import com.fasterxml.jackson.databind.*;
import jakarta.mail.internet.*;

import java.io.*;
import java.util.*;

import static com.predic8.membrane.core.http.MimeType.*;

public abstract class Message<T extends Body, S extends Message<T,S>> {

    protected Body body = new NoBody();
    protected ContentType mediaType;
    private final Headers headers = new Headers();

    public static class Headers {

        static class HeaderKey {

            private final String key;

            public HeaderKey(String key) {
                this.key = key;
            }

            @Override
            public int hashCode() {
                return key.toLowerCase().hashCode();
            }

            @Override
            public boolean equals(Object obj) {
                if (this == obj)
                    return true;
                if (obj == null || getClass() != obj.getClass())
                    return false;
                return this.key.equalsIgnoreCase(((HeaderKey) obj).key);
            }
        }

        private final Map<HeaderKey,String> fields = new HashMap<>();

        public void put(String key, String value) {
            fields.put(new HeaderKey(key),value);
        }

        public String get(String key) {
            return fields.get(new HeaderKey(key));
        }

        public int size() {
            return fields.size();
        }
    }

    protected Message() {
    }

    public Headers getHeaders() {
        return headers;
    }

    protected Message(String mediaType) throws ParseException {
        this.mediaType = new ContentType(mediaType);
    }

    public Body getBody() {
        return body;
    }


    public S body(Body body) {
        this.body = body;
        if (body instanceof JsonBody)
            this.mediaType = APPLICATION_JSON_CONTENT_TYPE;

        //noinspection unchecked
        return (S)this;
    }


    public S body(InputStream inputStream) {
        this.body = new InputStreamBody(inputStream);

        //noinspection unchecked
        return (S)this;
    }

    public S body(String s) {
        this.body = new StringBody(s);

        //noinspection unchecked
        return (S)this;
    }

    public S body(JsonNode n) {
        this.body = new JsonBody(n);
        this.mediaType = APPLICATION_JSON_CONTENT_TYPE;

        //noinspection unchecked
        return (S)this;
    }

    public S mediaType(String mediaType) throws ParseException {
        this.mediaType = new ContentType(mediaType);

        //noinspection unchecked
        return (S)this;
    }

    public S json() {
        this.mediaType = APPLICATION_JSON_CONTENT_TYPE;

        //noinspection unchecked
        return (S)this;
    }

    public boolean isOfMediaType(String mediaType) {
        // See https://datatracker.ietf.org/doc/html/rfc7231#appendix-D
        // media-range = ( "*/*" / ( type "/*" ) / ( type "/" subtype ) ) *( OWS
        //    ";" OWS parameter )
        // So */foo is illegal => We do not have to check that.
        if (mediaType.startsWith("*/*"))
            return true;
        return this.mediaType.match(mediaType);
    }

    public boolean hasBody() {
        if (body == null)
            return false;
        return !(body instanceof NoBody);
    }



    public ContentType getMediaType() {
        return mediaType;
    }
}
