/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.annot.yaml;

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
