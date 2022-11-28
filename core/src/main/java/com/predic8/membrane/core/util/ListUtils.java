package com.predic8.membrane.core.util;

import java.util.*;

public class ListUtils {

    public static List<String> stringList(String s1) {
        List<String> list = new ArrayList<>();
        list.add(s1);
        return list;
    }

    public static List<String> stringList(String s1, String s2) {
        List<String> list = stringList(s1);
        list.add(s2);
        return list;
    }

    public static List<String> stringList(String s1, String s2, String s3) {
        List<String> list = stringList(s1,s2);
        list.add(s3);
        return list;
    }
}
