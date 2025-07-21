package com.predic8.membrane.core.interceptor.dlp;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;

@MCElement(name = "mask")
public class Mask extends Action {

    private String keepRight = "0";

    @Override
    public String apply(DLPContext context) {
        try {
            DocumentContext doc = JsonPath.parse(context.body());
            String original = doc.read(getField(), String.class);
            String masked = maskKeepRight(original, Integer.parseInt(keepRight));
            doc.set(getField(), masked);
            return doc.jsonString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to apply mask on field: " + getField(), e);
        }
    }

    private String maskKeepRight(String input, int keepRight) {
        if (input == null || input.length() <= keepRight) return input;
        return "*".repeat(input.length() - keepRight) + input.substring(input.length() - keepRight);
    }

    public String getKeepRight() {
        return keepRight;
    }

    @MCAttribute
    public void setKeepRight(String keepRight) {
        if (keepRight != null) {
            try {
                if (Integer.parseInt(keepRight) < 0) {
                    throw new IllegalArgumentException("keepRight must be non-negative: " + keepRight);
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("keepRight must be a valid integer: " + keepRight, e);
            }
        }
        this.keepRight = keepRight;
    }
}
