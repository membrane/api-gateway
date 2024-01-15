package com.predic8.membrane.core.lang.spel.functions;

public class BuiltInFunctions {

    public static String test() {
        return "Hello World!";
    }

    public static String hello(String name) {
        return "Hello " + name;
    }

    public static Integer add(Integer a, Integer b) {
        return a + b;
    }
}
