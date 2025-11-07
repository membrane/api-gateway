package com.predic8.membrane.annot.generator.util;

public class SchemaGeneratorUtil {

    public static String escapeJsonContent(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            sb.append(escape(c));
        }
        return sb.toString();
    }

    static String escape(char c) {
        return switch (c) {
            case '"' -> "\\\"";
            case '\\' -> "\\\\";
            case '\b' -> "\\b";
            case '\f' -> "\\f";
            case '\n' -> "\\n";
            case '\r' -> "\\r";
            case '\t' -> "\\t";
            default -> {
                if (c < 0x20) yield String.format("\\u%04x", (int) c);
                else yield String.valueOf(c);
            }
        };
    }
}
