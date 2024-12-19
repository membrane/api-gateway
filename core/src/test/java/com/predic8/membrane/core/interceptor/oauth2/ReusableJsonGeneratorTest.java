/* Copyright 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.oauth2;

import com.fasterxml.jackson.core.JsonGenerator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class ReusableJsonGeneratorTest {

    @Test
    public void reusableJsonGenerator() throws IOException {

        String[] params = new String[]{
                "error", "invalid_request"
        };
        ReusableJsonGenerator jsonGen = new ReusableJsonGenerator();

        String json1 = generateJson(jsonGen, params);
        String json2 = generateJson(jsonGen, params);

        Assertions.assertEquals(json1, json2);
    }

    private String generateJson(ReusableJsonGenerator jsonGen, String[] params) throws IOException {
        String json = null;
        try (JsonGenerator gen = jsonGen.resetAndGet()) {
            gen.writeStartObject();
            for (int i = 0; i < params.length; i += 2)
                gen.writeObjectField(params[i], params[i + 1]);
            gen.writeEndObject();
            json = jsonGen.getJson();
        }
        return json;
    }

}
