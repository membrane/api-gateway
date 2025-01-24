/*
 * Copyright 2016 predic8 GmbH, www.predic8.com
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

package com.predic8.membrane.core.interceptor.oauth2;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class BufferedJsonGenerator implements AutoCloseable{
    private final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    private final JsonGenerator jsonGenerator;

    public BufferedJsonGenerator() {
        try {
            JsonFactory jsonFactory = new JsonFactory();
            jsonGenerator = jsonFactory.createGenerator(baos);
        } catch (IOException e) {
            throw new RuntimeException("Should not happen, as this is in-memory only.", e);
        }
    }

    public JsonGenerator getJsonGenerator() {
        return jsonGenerator;
    }

    public String getJson() throws IOException {
        jsonGenerator.flush();
        return baos.toString();
    }

    public String toString() {
        try {
            return getJson();
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public void close() {
        try {
            jsonGenerator.close();
        } catch (IOException e) {
            throw new RuntimeException("Should not happen, as this is in-memory only.", e);
        }
    }
}
