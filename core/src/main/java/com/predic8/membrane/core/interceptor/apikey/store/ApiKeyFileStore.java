package com.predic8.membrane.core.interceptor.apikey.store;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import org.springframework.context.*;
import org.springframework.context.event.*;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.stream;

@MCElement(name = "keyFileStore", topLevel = false)
public class ApiKeyFileStore implements ApiKeyStore, ApplicationListener<ContextStartedEvent> {

    private String location;

    private Map<String, List<String>> scopes;

    @Override
    public void onApplicationEvent(@SuppressWarnings("NullableProblems") ContextStartedEvent ignored) {
        init();
    }

    public void init() {
        try {
            scopes = readFile().stream()
                    .map(line -> line.split(":"))
                    .collect(Collectors.toMap(
                            parts -> parts[0],
                            parts -> stream(parts[1].split(",")).map(String::trim).toList()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public List<String> getScopes(String key) {
        return scopes.get(key);
    }

    public String getLocation() {
        return this.location;
    }

    @MCAttribute
    public void setLocation(String location) {
        this.location = location;
    }

    private List<String> readFile() throws IOException {
        try (FileInputStream fis = new FileInputStream(location)) {
            return stream(new String(fis.readAllBytes(), UTF_8).split("\n")).toList();
        }
    }



}

