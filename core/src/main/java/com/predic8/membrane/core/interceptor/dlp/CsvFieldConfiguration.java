package com.predic8.membrane.core.interceptor.dlp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

/**
 * Loads field risk mappings from a CSV file with format:
 * field_name,description,risk_level
 * where risk_level must be one of: high, medium, low, unclassified
 */
public class CsvFieldConfiguration implements FieldConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CsvFieldConfiguration.class);

    @Override
    public Map<String, String> getFields(String fileName) {
        Map<String, String> riskDict = new HashMap<>();

        try (InputStream inputStream = getResourceAsStream(fileName)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            boolean isHeader = true;

            while ((line = reader.readLine()) != null) {
                if (isHeader) {
                    isHeader = false;
                    continue;
                }

                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                String[] parts = line.split(",", -1);
                if (parts.length < 3) {
                    log.warn("Skipping invalid line (less than 3 columns): {}", line);
                    continue;
                }

                String field = parts[0].trim().toLowerCase(Locale.ROOT);
                String riskLevel = parts[2].trim().toLowerCase(Locale.ROOT);

                if (!isValidRiskLevel(riskLevel)) {
                    log.warn("Invalid risk level '{}' for field '{}'. Defaulting to 'unclassified'", riskLevel, field);
                    riskLevel = "unclassified";
                }

                riskDict.put(field, riskLevel);
            }

        } catch (IOException e) {
            throw new RuntimeException("Error reading CSV field configuration: " + fileName, e);
        }
        return riskDict;
    }

    private InputStream getResourceAsStream(String fileName) {
        InputStream is = CsvFieldConfiguration.class.getClassLoader().getResourceAsStream(fileName);
        if (is == null) {
            String msg = "Could not find CSV config file: " + fileName;
            log.error(msg);
            throw new IllegalArgumentException(msg);
        }
        return is;
    }

    private boolean isValidRiskLevel(String level) {
        return switch (level) {
            case "high", "medium", "low", "unclassified" -> true;
            default -> false;
        };
    }
}
