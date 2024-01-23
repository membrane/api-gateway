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
package com.predic8.membrane.core.interceptor.apikey.stores;

import com.predic8.membrane.annot.*;
import org.springframework.context.*;
import org.springframework.context.event.*;

import java.io.*;
import java.util.AbstractMap.*;
import java.util.*;
import java.util.stream.*;

import static com.predic8.membrane.core.interceptor.apikey.ApiKeyUtils.*;
import static java.util.Arrays.*;
import static java.util.Optional.of;
import static java.util.Optional.*;
import static java.util.stream.Collectors.*;

@MCElement(name = "keyFileStore", topLevel = false)
public class ApiKeyFileStore implements ApiKeyStore, ApplicationListener<ContextStartedEvent> {

    private String location;
    private Map<String, Optional<List<String>>> scopes;

    @SuppressWarnings("NullableProblems")
    @Override
    public void onApplicationEvent(ContextStartedEvent ignored) {
        try {
            scopes = readKeyData(readFile(location));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Map<String, Optional<List<String>>> readKeyData(Stream<String> lines) throws IOException {
        Map<String, Optional<List<String>>> collect;
        try {
            collect = lines
                    .map(ApiKeyFileStore::extractKeyBeforeHash)
                    .filter(line -> !line.isEmpty())
                    .map(ApiKeyFileStore::parseLine)
                    .collect(toMap(
                            SimpleEntry::getKey,
                            SimpleEntry::getValue));
        } catch (Exception e) {
            throw new RuntimeException("Cannot read file with API keys. Please make sure that there are no multiple entries with the same key! " + e);
        }
        return collect;
    }

    static String extractKeyBeforeHash(String line) {
        return line.split("#", 2)[0];
    }

    static SimpleEntry<String, Optional<List<String>>> parseLine(String line) {
        List<String> parts = getParts(line);
        return new SimpleEntry<>(parts.get(0), getValue(parts));
    }

    private static List<String> getParts(String line) {
        return stream(line.split(":", 2)).map(String::trim).toList();
    }

    private static Optional<List<String>> getValue(List<String> parts) {
        return (parts.size() > 1 && !parts.get(1).isEmpty()) ? of(parseValues(parts.get(1))) : empty();
    }

    static List<String> parseValues(String valuesPart) {
        return stream(valuesPart.split(","))
                .map(String::trim)
                .toList();
    }

    public Optional<List<String>> getScopes(String key) throws UnauthorizedApiKeyException {
        if (scopes.containsKey(key)) {
            return scopes.get(key);
        } else {
            throw new UnauthorizedApiKeyException();
        }
    }

    public String getLocation() {
        return this.location;
    }

    @MCAttribute
    public void setLocation(String location) {
        this.location = location;
    }

}