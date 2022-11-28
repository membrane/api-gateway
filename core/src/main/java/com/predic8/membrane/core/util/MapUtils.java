package com.predic8.membrane.core.util;

import java.util.*;

public class MapUtils {

    public static Map<String,String> stringMap(String k1,String v1) {
        Map<String,String> m = new HashMap<>();
        m.put(k1,v1);
        return m;
    }

    public static Map<String,String> stringMap(String k1,String v1, String k2,String v2) {
        Map<String,String> m = stringMap(k1,v1);
        m.put(k2,v2);
        return m;
    }
}
