package com.predic8.membrane.core.interceptor.dlp;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.InvalidPathException;
import com.jayway.jsonpath.JsonPath;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Masks the value referenced by a JSONPath expression, keeping the
 * n right-most characters visible (n = keepRight, default 0).
 * <p>
 * Example:
 * <mask field="$.creditcard" keepRight="4"/>
 * 4111111111111111  ?  ************1111
 */
@MCElement(name = "mask")
public class Mask {

    private static final Logger log = LoggerFactory.getLogger(Mask.class);
    private String field;

    private String keepRight;

    public String apply(String json) {
        try {
            DocumentContext context = JsonPath.parse(json);
            context.set(field, maskKeepRight(context.read(field, String.class), Integer.parseInt(keepRight)));
            return context.jsonString();
        } catch (Exception e) {
            throw new RuntimeException("DLP Filter failed to apply JSONPath deletions", e);
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

    public String getField() {
        return field;
    }

    @MCAttribute
    public void setField(String field) {
        this.field = field;
    }

    public String getKeepRight() {
        return keepRight;
    }

    @MCAttribute
    public void setKeepRight(String keepRight) {
        this.keepRight = keepRight;
    }
}
