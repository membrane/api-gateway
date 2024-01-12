package com.predic8.membrane.core.interceptor.apikey;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class ApiKeyUtils {
    public static Stream<String> readFile(String location) throws IOException {
        return Files.lines(Path.of(location));
    }
}
