package com.predic8.membrane.core.interceptor.dlp;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * @description <p>Masks the value of a sensitive field, leaving only the configured number of characters at the end visible.</p>
 *
 * <p>Useful for obfuscating data like credit card numbers or personal identifiers.</p>
 *
 * <h2>Attributes</h2>
 * <ul>
 *     <li>{@code field} ? The JSONPath to the field to mask.</li>
 *     <li>{@code keepRight} ? Number of characters at the end to keep visible. Defaults to 0.</li>
 * </ul>
 * @example <mask field="$.credit_card" keepRight="4" />
 */
@MCElement(name = "mask")
public class Mask extends Action {

    private static final Logger log = LoggerFactory.getLogger(Mask.class);

    private static final Configuration SAFE_CONFIG = Configuration.builder()
            .options(Set.of(Option.DEFAULT_PATH_LEAF_TO_NULL))
            .build();

    private String keepRight = "0";

    @Override
    public String apply(DLPContext context) {
        DocumentContext doc = JsonPath.using(SAFE_CONFIG).parse(context.body());
        String original = doc.read(getField(), String.class);

        if (original == null) {
            log.info("[Mask]: Field '{}' not found!", getField());
            return doc.jsonString();
        }

        String masked = maskKeepRight(original, Integer.parseInt(keepRight));
        doc.set(getField(), masked);
        log.info("[Mask]: Field='{}' | Value='{}' | Masked='{}'", getField(), original, masked);
        return doc.jsonString();
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
