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

public class Response extends Message<Response> {

    private final int statusCode;

    public Response(int statusCode) {
        this.statusCode = statusCode;
    }

    public Response(int statusCode, String mediaType) throws ParseException {
        super(mediaType);
        this.statusCode = statusCode;
    }

    public static Response statusCode(int statusCode) {
        return new Response(statusCode);
    }

    public Response body(Body body) {
        this.body = body;
        return this;
    }

    public Response body(InputStream inputStream) {
        this.body = new InputStreamBody(inputStream);
        return this;
    }

    public Response body(JsonNode n) {
        this.body = new JsonBody(n);
        return this;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public boolean sameStatusCode(String other) {
        return Integer.toString(statusCode).equals(other);
    }

    /**
     *
     * @param wildcard String like 2XX, 3XX, 4XX
     * @return true if statuscode matches wildcard
     */
    public boolean matchesWildcard(String wildcard) {
        if (wildcard.length() != 3 || !wildcard.endsWith("XX"))
            return false;

        return Integer.toString(statusCode).charAt(0) == wildcard.charAt(0);
    }

}
