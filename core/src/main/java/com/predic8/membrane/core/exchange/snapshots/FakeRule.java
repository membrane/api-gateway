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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.RuleKey;
import com.predic8.membrane.core.rules.StatisticCollector;
import com.predic8.membrane.core.transport.ssl.SSLContext;
import com.predic8.membrane.core.transport.ssl.SSLProvider;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@JsonSerialize(using = FakeRule.Serializer.class)
@JsonDeserialize(using = FakeRule.Deserializer.class)
public class FakeRule implements Rule {
    private final String toStringText;
    final FakeKey key;

    public static class Serializer extends JsonSerializer<FakeRule>{
        @Override
        public void serialize(FakeRule fakeRule, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
            jsonGenerator.writeStartObject();
            jsonGenerator.writeStringField("name",fakeRule.toString());
            jsonGenerator.writeNumberField("port",fakeRule.getKey().getPort());
            jsonGenerator.writeEndObject();
        }
    }

    public static class Deserializer extends JsonDeserializer<FakeRule>{
        @Override
        public FakeRule deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
            JsonNode node = jsonParser.getCodec().readTree(jsonParser);
            return new FakeRule(String.valueOf(node.get("name").asText()),node.get("port").asInt());
        }
    }

    public FakeRule(Rule rule) {
        this(rule.toString(),rule.getKey().getPort());
    }

    public FakeRule(String toStringText, int port) {
        this.toStringText = toStringText;
        this.key = new FakeKey(port);
    }

    @Override
    public String toString() {
        return toStringText;
    }

    @Override
    public List<Interceptor> getInterceptors() {
        return null;
    }

    @Override
    public void setInterceptors(List<Interceptor> interceptors) {

    }

    @Override
    public boolean isBlockRequest() {
        return false;
    }

    @Override
    public boolean isBlockResponse() {
        return false;
    }

    @Override
    public RuleKey getKey() {
        return key;
    }

    @Override
    public void setKey(RuleKey ruleKey) {

    }

    @Override
    public void setName(String name) {

    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public void setBlockRequest(boolean blockStatus) {

    }

    @Override
    public void setBlockResponse(boolean blockStatus) {

    }

    @Override
    public void collectStatisticsFrom(Exchange exc) {

    }

    @Override
    public Map<Integer, StatisticCollector> getStatisticsByStatusCodes() {
        return null;
    }

    @Override
    public int getCount() {
        return 0;
    }

    @Override
    public SSLContext getSslInboundContext() {
        return null;
    }

    @Override
    public SSLProvider getSslOutboundContext() {
        return null;
    }

    @Override
    public void init(Router router) throws Exception {

    }

    @Override
    public boolean isTargetAdjustHostHeader() {
        return false;
    }

    @Override
    public boolean isActive() {
        return false;
    }

    @Override
    public String getErrorState() {
        return null;
    }

    @Override
    public Rule clone() throws CloneNotSupportedException {
        return null;
    }
}
