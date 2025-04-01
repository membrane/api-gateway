/* Copyright 2018 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.exchange.snapshots;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.*;
import com.predic8.membrane.core.proxies.*;

import java.io.*;

/**
 * Used to store a reference to Proxys in a PersistentExchangeStore.
 */
@JsonSerialize(using = FakeProxy.Serializer.class)
@JsonDeserialize(using = FakeProxy.Deserializer.class)
public class FakeProxy extends AbstractProxy {

    public FakeProxy(Proxy proxy) {
        this(proxy.getKey().getPort());
        setName(proxy.getName());
    }

    public FakeProxy(int port) {
        this.key = new FakeKey(port);
    }

    public static class Serializer extends JsonSerializer<FakeProxy>{
        @Override
        public void serialize(FakeProxy fakeRule, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
            jsonGenerator.writeStartObject();
            jsonGenerator.writeStringField("name",fakeRule.getName());
            jsonGenerator.writeNumberField("port",fakeRule.getKey().getPort());
            jsonGenerator.writeEndObject();
        }
    }

    public static class Deserializer extends JsonDeserializer<FakeProxy>{
        @Override
        public FakeProxy deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
            JsonNode jsonNode = jsonParser.getCodec().<JsonNode>readTree(jsonParser);
            FakeProxy fp = new FakeProxy(jsonNode.get("port").asInt());
            fp.setName(jsonNode.get("name").asText());
            return fp;
        }
    }
}
