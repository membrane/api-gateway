package com.predic8.membrane.core.interceptor.dlp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

/**
 * Loads field risk mappings from CSV file with format:
 * field_name,description,risk_level
 * where risk_level should be one of: high, medium, low, unclassified
 */
public class CsvFieldConfiguration implements FieldConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CsvFieldConfiguration.class);

    // Optional: define allowed risk levels
    private static final Set<String> ALLOWED_RISK_LEVELS = Set.of("high", "medium", "low", "unclassified");

    @Override
    public Map<String, String> getFields(String fileName) {
        try (InputStream inputStream = CsvFieldConfiguration.class.getClassLoader().getResourceAsStream(fileName)) {
            if (inputStream == null) {
                log.error("Could not find file: {}", fileName);
                throw new NullPointerException("InputStream is null. File not found: " + fileName);
            }

            Map<String, String> riskDict = new HashMap<>();
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            boolean isHeader = true;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (isHeader) {
                    isHeader = false;
                    continue;
                }
                if (line.isEmpty() || line.startsWith("#")) continue;

                String[] parts = line.split(",", -1);
                if (parts.length >= 2) {
                    String field = parts[0].trim().toLowerCase(Locale.ROOT);
                    String riskLevel = parts[parts.length - 1].trim().toLowerCase(Locale.ROOT);

                    if (!ALLOWED_RISK_LEVELS.contains(riskLevel)) {
                        log.warn("Unknown risk level '{}' for field '{}'", riskLevel, field);
                    }

                    riskDict.put(field, riskLevel);
                } else {
                    log.warn("Invalid CSV line (too few columns): {}", line);
                }
            }

            return riskDict;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load risk data from " + fileName, e);
        }
    }
}
