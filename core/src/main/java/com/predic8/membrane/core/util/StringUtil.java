/* Copyright 2012 predic8 GmbH, www.predic8.com

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

import static java.lang.Math.min;

public class StringUtil {

    /**
     *
     * @param s
     * @param maxLength
     * @return
     */
    public static String truncateAfter(String s, int maxLength) {
        return maskNonPrintableCharacters(s.substring(0, min(s.length(), maxLength)));
    }

    private static String maskNonPrintableCharacters(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c >= 32 && c <= 126) {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        return sb.toString();
    }


}
