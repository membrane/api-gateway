package com.predic8.membrane.core.interceptor.dlp;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;

@MCElement(name = "mask")
public class Mask extends Action {

    private String keepRight = "0";

    @Override
    public String apply(String json) {
        try {
            DocumentContext context = JsonPath.parse(json);
            context.set(getField(), maskKeepRight(context.read(getField(), String.class), Integer.parseInt(keepRight)));
            return context.jsonString();
        } catch (Exception e) {
            throw new RuntimeException();
        }
    }

    private String maskKeepRight(String input, int keepRight) {
        if (input == null || input.length() <= keepRight) {
            return input;
        }
        int maskLength = input.length() - keepRight;
        StringBuilder masked = new StringBuilder();
        for (int i = 0; i < maskLength; i++) {
            masked.append("*");
        }
        masked.append(input.substring(maskLength));
        return masked.toString();
    }

    public String getKeepRight() {
        return keepRight;
    }

    @MCAttribute
    public void setKeepRight(String keepRight) {
        this.keepRight = keepRight;
    }
}
