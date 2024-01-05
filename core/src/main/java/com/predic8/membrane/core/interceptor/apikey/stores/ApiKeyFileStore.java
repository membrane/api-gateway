package com.predic8.membrane.core.interceptor.apikey.stores;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextStartedEvent;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.AbstractMap.SimpleEntry;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Arrays.stream;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.stream.Collectors.toList;

@MCElement(name = "keyFileStore", topLevel = false)
public class ApiKeyFileStore implements ApiKeyStore, ApplicationListener<ContextStartedEvent> {

    private String location;
    private Map<String, Optional<List<String>>> scopes;

    @SuppressWarnings("NullableProblems")
    @Override
    public void onApplicationEvent(ContextStartedEvent ignored) {
        try {
            scopes = readKeyData(readFile());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Map<String, Optional<List<String>>> readKeyData(Stream<String> lines) throws IOException {
        return lines
                .map(ApiKeyFileStore::parseLine).distinct()
                .collect(Collectors.toMap(
                        SimpleEntry::getKey,
                        SimpleEntry::getValue));
    }

    static SimpleEntry<String, Optional<List<String>>> parseLine(String line) {
        String[] parts = line.split(":", 2);
        Optional<List<String>> value = (parts.length > 1 && !parts[1].trim().isEmpty()) ? of(parseValues(parts[1])) : empty();
        return new SimpleEntry<>(parts[0].trim(), value);
    }

    static List<String> parseValues(String valuesPart) {
        return stream(valuesPart.split(","))
                .map(String::trim)
                .collect(toList());
    }

    public Optional<List<String>> getScopes(String key) {
        return scopes.get(key);
    }

    public String getLocation() {
        return this.location;
    }

    @MCAttribute
    public void setLocation(String location) {
        this.location = location;
    }

    public Stream<String> readFile() throws IOException {
        return Files.lines(Path.of(location));
    }
}

