package com.predic8.membrane.core.lang.spel;

public class BuiltInFunctions {

    /**
     * BuiltIn Annotation raus, alle public
     * @return
     */
    @BuiltIn
    public static String test() {
        return "Hello World!";
    }

    public static String hello(String name) {
        return "Hello " + name;
    }
}
