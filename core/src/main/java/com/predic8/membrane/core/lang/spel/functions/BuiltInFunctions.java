package com.predic8.membrane.core.lang.spel.functions;

import com.predic8.membrane.core.lang.spel.ExchangeEvaluationContext;

public class BuiltInFunctions {

    public static String test(ExchangeEvaluationContext ctx) {
        return "Hello World!";
    }

    public static String hello(String name, ExchangeEvaluationContext ctx) {
        return "Hello " + name;
    }

    public static Integer add(Integer a, Integer b, ExchangeEvaluationContext ctx) {
        return a + b;
    }
}
