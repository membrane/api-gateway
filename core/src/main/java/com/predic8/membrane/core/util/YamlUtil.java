package com.predic8.membrane.core.util;

public class YamlUtil {

    /**
      * Removes the first YAML document start marker (---) from the input.
      * <p>
      * Note: This method normalizes line endings to \n and ensures the output
      * ends with a newline character.
      *
      * @param yaml the YAML string to process
      * @return the processed YAML with the first marker removed, or null if input is null
      */
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
