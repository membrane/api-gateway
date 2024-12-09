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

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;

import java.io.IOException;
import java.util.AbstractMap.SimpleEntry;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static com.predic8.membrane.core.interceptor.apikey.ApiKeyUtils.readFile;
import static java.util.Arrays.stream;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.stream.Collectors.toMap;

/**
 * @description Loads api keys from a file. File has to be one key per line, blank lines for formatting are allowed. Optionally, a comma separated list of scopes after the key and a colon in between the two. Hash symbol can be used for comments at the end of each line, including empty lines.
 */
@MCElement(name = "keyFileStore", topLevel = false)
public class ApiKeyFileStore implements ApiKeyStore {

    private String location;
    private Map<String, Optional<List<String>>> scopes;

    @Override
    public void init(Router router) {
        try {
            scopes = readKeyData(readFile(location, router.getResolverMap(), router.getBaseLocation()));
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

    @Override
    public Optional<List<String>> getScopes(String key) throws UnauthorizedApiKeyException {
        if (scopes.containsKey(key)) {
            return scopes.get(key);
        } else {
            throw new UnauthorizedApiKeyException();
        }
    }

    /**
     * @description Path/URL to the api key file.
     */
    @MCAttribute
    public void setLocation(String location) {
        this.location = location;
    }

    public String getLocation() {
        return this.location;
    }
}