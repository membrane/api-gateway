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
        return s.substring(0, min(s.length(), maxLength));
    }
}
