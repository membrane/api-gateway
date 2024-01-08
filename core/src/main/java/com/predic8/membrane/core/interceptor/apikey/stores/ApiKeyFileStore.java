package com.predic8.membrane.core.interceptor.apikey.stores;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextStartedEvent;

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
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

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
                    .map(ApiKeyFileStore::parseLine)
                    .collect(toMap(
                            SimpleEntry::getKey,
                            SimpleEntry::getValue));
        } catch (Exception e) {
            throw new RuntimeException("Cannot read file with API keys. Please make sure that there are no multiple entries with the same key! " + e);
        }
        return collect;
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
                .collect(toList());
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