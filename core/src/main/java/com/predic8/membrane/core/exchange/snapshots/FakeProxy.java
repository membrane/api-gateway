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

import com.predic8.membrane.core.proxies.AbstractProxy;
import com.predic8.membrane.core.proxies.Proxy;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.*;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;

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

    public static class Serializer extends ValueSerializer<FakeProxy> {
        @Override
        public void serialize(FakeProxy fakeRule, JsonGenerator gen, SerializationContext ctxt) throws JacksonException {
            gen.writeStartObject();
            gen.writeStringProperty("name", fakeRule.getName());
            gen.writeNumberProperty("port", fakeRule.getKey().getPort());
            gen.writeEndObject();
        }
    }

    public static class Deserializer extends ValueDeserializer<FakeProxy> {
        @Override
        public FakeProxy deserialize(JsonParser p, DeserializationContext deserializationContext) throws JacksonException {
            JsonNode node = p.readValueAsTree();
            JsonNode portNode = node.get("port");
            JsonNode nameNode = node.get("name");
            FakeProxy fp = new FakeProxy(portNode != null ? portNode.intValue() : 0);
            fp.setName(nameNode != null ? nameNode.stringValue() : null);
            return fp;
        }
    }
}
