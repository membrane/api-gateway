package com.predic8.membrane.core.interceptor.json.rpc;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.Required;
import com.predic8.membrane.core.util.ConfigurationException;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public abstract class Rule {

    private String method;
    private Pattern methodPattern;

    public boolean matches(String method) {
        return methodPattern != null && methodPattern.matcher(method).matches();
    }

    abstract boolean permits();

    @Required
    @MCAttribute
    public void setMethod(String method) {
        if (method == null || method.trim().isEmpty()) {
            throw new ConfigurationException("method must not be empty");
        }

        this.method = method.trim();
        try {
            methodPattern = Pattern.compile(this.method);
        } catch (PatternSyntaxException e) {
            throw new ConfigurationException("Invalid method regex: " + this.method);
        }
    }

    public String getMethod() {
        return method;
    }
}
