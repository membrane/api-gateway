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
import org.slf4j.*;

import java.io.*;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON_CONTENT_TYPE;

public abstract class Message<T> {

    private static final Logger log = LoggerFactory.getLogger(Message.class.getName());

    protected Body body = new NoBody();
    protected ContentType mediaType;

    protected Message() {
    }

    protected Message(String mediaType) throws ParseException {
        this.mediaType = new ContentType(mediaType);
    }

    public Body getBody() {
        return body;
    }

    @SuppressWarnings("unchecked")
    public T body(Body body) {
        this.body = body;
        if (body instanceof JsonBody)
            this.mediaType = APPLICATION_JSON_CONTENT_TYPE;
        return (T) this;
    }

    @SuppressWarnings("unchecked")
    public T body(InputStream inputStream) {
        this.body = new InputStreamBody(inputStream);
        return (T) this;
    }

    @SuppressWarnings("unchecked")
    public T body(String s) {
        this.body = new StringBody(s);
        return (T) this;
    }

    @SuppressWarnings("unchecked")
    public T body(JsonNode n) {
        this.body = new JsonBody(n);
        this.mediaType = APPLICATION_JSON_CONTENT_TYPE;
        return (T) this;
    }

    @SuppressWarnings("unchecked")
    public T mediaType(String mediaType) throws ParseException {
        this.mediaType = new ContentType(mediaType);
        return (T) this;
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

    @SuppressWarnings("unchecked")
    public T json() {
        this.mediaType = APPLICATION_JSON_CONTENT_TYPE;
        return (T) this;
    }

    public ContentType getMediaType() {
        return mediaType;
    }
}
