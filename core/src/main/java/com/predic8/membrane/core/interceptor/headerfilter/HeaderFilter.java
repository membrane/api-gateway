package com.predic8.membrane.core.interceptor.headerfilter;

import com.predic8.membrane.core.http.*;
import org.slf4j.*;

import java.util.*;

public class HeaderFilter {

    private static final Logger log = LoggerFactory.getLogger(HeaderFilter.class);

    private List<HeaderFilterRule> rules = new ArrayList<>();

    void filter(Header h) {
        for (HeaderField hf : h.getAllHeaderFields()) {
            for (HeaderFilterRule r : rules) {
                // When the rule matches carry on with the next header field
                // otherwise check the next rule
                if (filterField(h, hf, r)) break;
            }
        }
    }

    /**
     * @param header The header with all header fields.
     * @param headerField The header field to check.
     * @param rule The rule to check.
     * @return true if the header field has matched => continue with the next header field
     */
    private static boolean filterField(Header header, HeaderField headerField, HeaderFilterRule rule) {
        if (!rule.matches(headerField))
            return false;

        if (rule.getAction() == HeaderFilterInterceptor.Action.REMOVE) {
            log.debug("Removing HTTP header {}", headerField.getHeaderName().toString());
            header.remove(headerField);
        }
        return true;
    }

    public List<HeaderFilterRule> getRules() {
        return rules;
    }

    public void setRules(List<HeaderFilterRule> rules) {
        this.rules = rules;
    }
}
