package com.predic8.membrane.core.util;

import static java.util.stream.Collectors.*;

public class YamlUtil {

    public static String removeYamlDocStartMarkers(String yaml) {
        if (yaml == null) return null;

        return yaml
                .lines()
                .filter(line -> !line.stripLeading().startsWith("---"))
                .collect(joining("\n"));
    }

}
