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
import com.predic8.membrane.core.openapi.model.*;
import com.predic8.membrane.core.openapi.util.*;

import java.io.*;

abstract class Message<T> {

    protected Body body = new NoBody();
    protected String mediaType;

    public Body getBody() {
        return body;
    }

    @SuppressWarnings("unchecked")
    public T body(Body body) {
        this.body = body;
        if (body instanceof JsonBody)
            this.mediaType("application/json");
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
        this.mediaType("application/json");
        return (T) this;
    }

    @SuppressWarnings("unchecked")
    public T mediaType(String mediaType) {
        this.mediaType = Utils.getMediaTypeFromContentTypeHeader(mediaType);
        return (T) this;
    }

    public boolean hasBody() {
        if (body == null)
            return false;
        return !(body instanceof NoBody);
    }

    @SuppressWarnings("unchecked")
    public T json() {
        this.mediaType("application/json");
        return (T) this;
    }

    public String getMediaType() {
        return mediaType;
    }
}
