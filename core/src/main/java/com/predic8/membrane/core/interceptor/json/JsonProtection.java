/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.json;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.google.common.io.CountingInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION;
import static com.fasterxml.jackson.core.JsonTokenId.*;

/**
 * Streams a JSON document through a Jackson parser and enforces a set of {@link JsonLimits} on it,
 * without materialising the document. Used by {@link JsonProtectionInterceptor} on a whole request
 * body as well as on the individual JSON parts of a multipart body.
 *
 * <p>Stateless with respect to a single document: one instance can scan any number of documents,
 * also concurrently.</p>
 */
public class JsonProtection {

    /** Duplicate keys are an attack in their own right, so the parser rejects them itself. */
    private final JsonFactory jsonFactory = new JsonFactory().enable(STRICT_DUPLICATE_DETECTION);

    private final JsonLimits limits;

    public JsonProtection(JsonLimits limits) {
        this.limits = limits;
    }

    /**
     * Reads the document and returns normally if it stays within the limits.
     *
     * @throws JsonProtectionException if a limit is exceeded or the document is not well-formed JSON
     * @throws IOException             on I/O errors, and as {@link com.fasterxml.jackson.core.JsonParseException}
     *                                 on malformed JSON
     */
    public void scan(InputStream body) throws IOException, JsonProtectionException {
        CountingInputStream cis = new CountingInputStream(body);
        JsonParser parser = jsonFactory.createParser(cis);
        int tokenCount = 0;
        List<Context> contexts = new ArrayList<>();
        while (true) {
            JsonToken jsonToken = parser.nextValue();
            if (jsonToken == null)
                break;
            if (++tokenCount > limits.maxTokens())
                throw JsonProtectionException.at("Exceeded maxTokens.", parser);
            checkSize(cis, parser);
            if (!contexts.isEmpty())
                contexts.getLast().check(jsonToken, parser);
            switch (jsonToken.id()) {
                case ID_START_OBJECT -> push(contexts, new ObjContext(limits), parser);
                case ID_START_ARRAY -> push(contexts, new Context(limits.maxArraySize(), "Exceeded maxArraySize."), parser);
                case ID_END_OBJECT, ID_END_ARRAY -> pop(contexts, parser);
                case ID_STRING -> {
                    if (parser.getValueAsString().length() > limits.maxStringLength())
                        throw JsonProtectionException.at("Exceeded maxStringLength.", parser);
                }
                case ID_NUMBER_INT, ID_NUMBER_FLOAT, ID_TRUE, ID_FALSE, ID_NULL -> {
                }
                default -> throw JsonProtectionException.at("Not handled (" + jsonToken.id() + ")", parser);
            }
        }
        checkSize(cis, parser);
    }

    private void checkSize(CountingInputStream cis, JsonParser parser) throws JsonProtectionException {
        if (limits.exceedsMaxSize(cis.getCount()))
            throw JsonProtectionException.at("Exceeded maxSize.", parser);
    }

    private void push(List<Context> contexts, Context context, JsonParser parser) throws JsonProtectionException {
        if (contexts.size() >= limits.maxDepth())
            throw JsonProtectionException.at("Exceeded maxDepth.", parser);
        contexts.add(context);
    }

    private static void pop(List<Context> contexts, JsonParser parser) throws JsonProtectionException {
        if (contexts.isEmpty())
            throw JsonProtectionException.at("Invalid JSON Document.", parser);
        contexts.removeLast();
    }

    /**
     * Counts the members of the object or array currently being parsed. The end token closing the
     * context is not a member and is therefore not counted.
     */
    private static class Context {
        private final int maxMembers;
        private final String exceededMessage;
        private int members;

        Context(int maxMembers, String exceededMessage) {
            this.maxMembers = maxMembers;
            this.exceededMessage = exceededMessage;
        }

        void check(JsonToken jsonToken, JsonParser parser) throws JsonProtectionException, IOException {
            if (isEndToken(jsonToken))
                return;
            if (++members > maxMembers)
                throw JsonProtectionException.at(exceededMessage, parser);
        }

        static boolean isEndToken(JsonToken jsonToken) {
            return jsonToken.id() == ID_END_OBJECT || jsonToken.id() == ID_END_ARRAY;
        }
    }

    /** Additionally checks the key each member is bound to. */
    private static class ObjContext extends Context {
        private final JsonLimits limits;

        ObjContext(JsonLimits limits) {
            super(limits.maxObjectSize(), "Exceeded maxObjectSize.");
            this.limits = limits;
        }

        @Override
        void check(JsonToken jsonToken, JsonParser parser) throws JsonProtectionException, IOException {
            if (isEndToken(jsonToken))
                return;
            super.check(jsonToken, parser);
            if (limits.blockProto() && "__proto__".equals(parser.currentName()))
                throw JsonProtectionException.at("__proto__ found as key.", parser);
            if (parser.currentName().length() > limits.maxKeyLength())
                throw JsonProtectionException.at("Exceeded maxKeyLength.", parser);
        }
    }
}
