/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.util;

public class TemplateUtil {

    /**
     * Checks if the provided string contains the template marker "${"
     * Fast implementation.
     * @param s the string to be checked for the presence of a template marker
     * @return true if the string contains a template marker, false otherwise
     */
    public static boolean containsTemplateMarker(String s) {
        if (s == null) return false;
        for (int i = 0, len = s.length() - 1; i < len; i++) {
            if (s.charAt(i) == '$' && s.charAt(i + 1) == '{') {
                return true;
            }
        }
        return false;
    }
}
