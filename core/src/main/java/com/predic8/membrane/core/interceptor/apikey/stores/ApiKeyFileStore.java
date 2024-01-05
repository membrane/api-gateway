package com.predic8.membrane.core.interceptor.apikey.stores;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextStartedEvent;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toMap;

@MCElement(name = "keyFileStore", topLevel = false)
public class ApiKeyFileStore implements ApiKeyStore, ApplicationListener<ContextStartedEvent> {

    private String location;
    private Map<String, List<String>> scopes;

    @SuppressWarnings("NullableProblems")
    @Override
    public void onApplicationEvent(ContextStartedEvent ignored) {
        try {
            scopes = readKeyData(readFile());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Map<String, List<String>> readKeyData(List<String> lines) {
            return lines.stream()
                    .map(line -> line.split(":"))
                    .collect(toMap(
                            parts -> parts[0],
                            parts -> stream(parts[1].split(",")).map(String::trim).toList()));
    }

    public List<String> getScopes(String key) {
        return scopes.getOrDefault(key, new ArrayList<>());
    }

    public String getLocation() {
        return this.location;
    }

    @MCAttribute
    public void setLocation(String location) {
        this.location = location;
    }

    public List<String> readFile() throws IOException {
        // Read per line split, store See: BufferedInputStream
        try (FileInputStream fis = new FileInputStream(location)) {
            return stream(new String(fis.readAllBytes(), UTF_8).split("\n")).toList();
        }
    }
}

