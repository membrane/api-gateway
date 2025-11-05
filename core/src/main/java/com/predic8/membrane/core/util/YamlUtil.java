package com.predic8.membrane.core.util;

public class YamlUtil {

    public static String removeFirstYamlDocStartMarker(String yaml) {
        if (yaml == null) return null;

        String[] lines = yaml.split("\\R"); // split on any line break
        StringBuilder sb = new StringBuilder();

        boolean removed = false;
        for (String line : lines) {
            if (!removed && line.stripLeading().startsWith("---")) {
                removed = true; // skip the first such line
                continue;
            }
            sb.append(line).append("\n");
        }

        return sb.toString();
    }

}
