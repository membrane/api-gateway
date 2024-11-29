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

public class ReusableJsonGenerator {
    JsonFactory jsonFactory = new JsonFactory();
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    JsonGenerator jsonGenerator;

    public ReusableJsonGenerator() {
        try {
            jsonGenerator = jsonFactory.createGenerator(baos);
        } catch (IOException e) {
        }
    }

    public JsonGenerator resetAndGet() {
        baos.reset();
        try {
            jsonGenerator = jsonFactory.createGenerator(baos);
        } catch (IOException e) {
        }
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
}
