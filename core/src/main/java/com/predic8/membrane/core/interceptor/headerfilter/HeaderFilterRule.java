package com.predic8.membrane.core.interceptor.headerfilter;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.headerfilter.HeaderFilterInterceptor.*;

import java.util.regex.*;

import static com.predic8.membrane.core.interceptor.headerfilter.HeaderFilterInterceptor.Action.KEEP;
import static com.predic8.membrane.core.interceptor.headerfilter.HeaderFilterInterceptor.Action.REMOVE;
import static java.util.regex.Pattern.*;

public class HeaderFilterRule {

    private final Action action;

    private String pattern;
    private Pattern p;

    public HeaderFilterRule(Action action) {
        this.action = action;
    }

    public HeaderFilterRule(String pattern, Action action) {
        this(action);
        setPattern(pattern);
    }

    public static HeaderFilterRule remove(String pattern) {
        return new HeaderFilterRule(pattern, REMOVE);
    }

    public static HeaderFilterRule keep(String pattern) {
        return new HeaderFilterRule(pattern, KEEP);
    }

    public String getPattern() {
        return pattern;
    }

    @MCTextContent
    public void setPattern(String pattern) {
        this.pattern = pattern;
        p = Pattern.compile(pattern, CASE_INSENSITIVE);
    }

    public boolean matches(HeaderField hf) {
        return matches(hf.getHeaderName().getName());
    }

    public boolean matches(String header) {
        return p.matcher(header).matches();
    }

    public Action getAction() {
        return action;
    }

}
