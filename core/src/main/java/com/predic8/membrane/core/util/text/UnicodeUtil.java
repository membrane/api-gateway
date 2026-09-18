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
package com.predic8.membrane.core.util.text;

public class UnicodeUtil {

    private UnicodeUtil() {}

    /** UTF-8 (EF BB BF) and UTF-16 (FE FF / FF FE) byte order marks, the only ones the XML parser itself detects. */
    public static boolean startsWithByteOrderMark(byte[] prefix, int length) {
        if (length >= 3 && (prefix[0] & 0xFF) == 0xEF && (prefix[1] & 0xFF) == 0xBB && (prefix[2] & 0xFF) == 0xBF) {
            return true;
        }
        if (length >= 2) {
            int first = prefix[0] & 0xFF;
            int second = prefix[1] & 0xFF;
            return (first == 0xFE && second == 0xFF) || (first == 0xFF && second == 0xFE);
        }
        return false;
    }
}
