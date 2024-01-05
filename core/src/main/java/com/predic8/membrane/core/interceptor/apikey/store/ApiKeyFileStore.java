package com.predic8.membrane.core.interceptor.apikey.apikeystore;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextStartedEvent;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toMap;

@MCElement(name = "keyFileStore", topLevel = false)
public class ApiKeyFileStore implements ApiKeyStore, ApplicationListener<ContextStartedEvent> {

    private String location;
    private boolean refresh = false;
    private Map<String, List<String>> scopes;

    @SuppressWarnings("NullableProblems")
    @Override
    public void onApplicationEvent(ContextStartedEvent ignored) {
        scopes = readKeyData();
    }

    public Map<String, List<String>> readKeyData() {
        try {
            return readFile().stream()
                    .map(line -> line.split(":"))
                    .collect(toMap(
                            parts -> parts[0],
                            parts -> stream(parts[1].split(",")).map(String::trim).toList()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public List<String> getScopes(String key) {
        if (refresh) {
            scopes = readKeyData();
        }
        return scopes.get(key);
    }

    public String getLocation() {
        return this.location;
    }

    @MCAttribute
    public void setLocation(String location) {
        this.location = location;
    }

    @MCAttribute
    public void setRefresh(boolean refresh) {
        this.refresh = refresh;
    }

    public List<String> readFile() throws IOException {
        try (FileInputStream fis = new FileInputStream(location)) {
            return stream(new String(fis.readAllBytes(), UTF_8).split("\n")).toList();
        }
    }
}

