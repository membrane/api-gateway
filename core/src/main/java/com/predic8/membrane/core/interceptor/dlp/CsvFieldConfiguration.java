package com.predic8.membrane.core.interceptor.dlp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class CsvFieldConfiguration implements FieldConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CsvFieldConfiguration.class);

    @Override
    public Map<String, String> getFields(String fileName) {
        try (InputStream inputStream = CsvFieldConfiguration.class.getClassLoader().getResourceAsStream(fileName)) {
            Map<String, String> riskDict = new HashMap<>();
            if (inputStream == null) {
                log.error("Could not find file: {}", fileName);
                throw new NullPointerException("InputStream is null. File not found: " + fileName);
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            boolean isHeader = true;

            while ((line = reader.readLine()) != null) {
                if (isHeader) {
                    isHeader = false;
                    continue;
                }

                String[] parts = line.split(",", -1);
                if (parts.length >= 3) {
                    String field = parts[0].trim().toLowerCase();
                    String riskLevel = parts[2].trim();
                    riskDict.put(field, riskLevel);
                } else {
                    log.warn("Invalid CSV line: {}", line);
                }
            }
            return riskDict;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load risk data from " + fileName, e);
        }
    }
}
