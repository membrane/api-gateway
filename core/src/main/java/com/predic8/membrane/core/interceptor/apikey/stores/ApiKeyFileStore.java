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
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.util.*;

import java.io.*;
import java.util.AbstractMap.*;
import java.util.*;
import java.util.stream.*;

import static com.predic8.membrane.core.interceptor.apikey.ApiKeyUtils.*;
import static java.util.Arrays.*;
import static java.util.Optional.of;
import static java.util.Optional.*;
import static java.util.stream.Collectors.*;

/**
 * @description Loads API keys and optional scopes from a text file. Each non-empty line must contain a key.
 * <ul>
 *   <li>Blank lines are ignored.</li>
 *   <li>A hash sign ({@code #}) starts a comment (line or end-of-line).</li>
 *   <li>Scopes can follow the key, separated by a colon. Multiple scopes are comma-separated.</li>
 * </ul>
 * <p>
 * Example file:
 * </p>
 * <pre>
 * # demo API keys
 * 123456                 # key without scopes
 * 7890:read,write        # key with two scopes
 *
 * # another valid key
 * abcd:admin
 * </pre>
 * @example See:
 * <a href="https://github.com/membrane/api-gateway/blob/master/distribution/examples/security/api-key/simple/demo-keys.txt" target="_blank">
 * GitHub example file
 * </a>
 * @topic 3. Security and Validation
 */
@MCElement(name = "apiKeyFileStore")
public class ApiKeyFileStore implements ApiKeyStore {

    private String location;
    private Map<String, Optional<Set<String>>> scopes;

    @Override
    public void init(Router router) {
        try {
            scopes = readKeyData(readFile(location, router.getResolverMap(), router.getBaseLocation()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Map<String, Optional<Set<String>>> readKeyData(Stream<String> lines) throws IOException {
        Map<String, Optional<Set<String>>> collect;
        try {
            collect = lines
                    .map(ApiKeyFileStore::extractKeyBeforeHash)
                    .filter(line -> !line.isEmpty())
                    .map(ApiKeyFileStore::parseLine)
                    .collect(toMap(
                            SimpleEntry::getKey,
                            SimpleEntry::getValue));
        } catch (Exception e) {
            throw new ConfigurationException("Failed to read API key file: " + e);
        }
        return collect;
    }

    static String extractKeyBeforeHash(String line) {
        return line.split("#", 2)[0];
    }

    static SimpleEntry<String, Optional<Set<String>>> parseLine(String line) {
        List<String> parts = getParts(line);
        return new SimpleEntry<>(parts.getFirst(), getValue(parts));
    }

    private static List<String> getParts(String line) {
        return stream(line.split(":", 2)).map(String::trim).toList();
    }

    private static Optional<Set<String>> getValue(List<String> parts) {
        return (parts.size() > 1 && !parts.get(1).isEmpty()) ? of(parseValues(parts.get(1))) : empty();
    }

    static Set<String> parseValues(String valuesPart) {
        return stream(valuesPart.split(","))
                .map(String::trim)
                .collect(toSet());
    }

    @Override
    public Optional<Set<String>> getScopes(String key) throws UnauthorizedApiKeyException {
        if (scopes.containsKey(key)) {
            return scopes.get(key);
        } else {
            throw new UnauthorizedApiKeyException();
        }
    }

    /**
     * @description Path or URL to the API key file. Can point to local files or classpath resources.
     * @example classpath:demo-keys.txt
     */
    @MCAttribute
    public void setLocation(String location) {
        this.location = location;
    }

    public String getLocation() {
        return this.location;
    }
}