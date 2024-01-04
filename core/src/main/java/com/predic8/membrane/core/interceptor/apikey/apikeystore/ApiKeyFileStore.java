package com.predic8.membrane.core.interceptor.apikey.apikeystore;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.*;

import static groovyjarjarantlr4.v4.runtime.misc.Utils.readFile;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.stream;

@MCElement(name = "ApiKeyFileStore", topLevel = false)
public class ApiKeyFileStore implements ApiKeyStore {

    private String location;

    @Override
        public Map<String, List<String>> getScopes() {
            try {
                return readFile().stream()
                        .map(line -> line.split(":"))
                        .collect(Collectors.toMap(
                                parts -> parts[0],
                                parts -> stream(parts[1].split(",")).map(String::trim).toList()));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

    public String getLocation() {
        return this.location;
    }

    @MCAttribute
    public void setLocation(String location) {
        this.location = location;
    }

    public List<String> readFile() throws IOException {
        try (FileInputStream fis = new FileInputStream(location)) {
            return stream(new String(fis.readAllBytes(), UTF_8).split("\n")).toList();
        }
    }
}

