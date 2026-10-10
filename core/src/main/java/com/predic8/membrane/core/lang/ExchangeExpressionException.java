/* Copyright 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.lang;

import com.predic8.membrane.core.exceptions.*;
import com.predic8.membrane.core.interceptor.Interceptor.*;

import java.util.*;

import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.*;

/**
 * Gathers information about an error evaluating an exchange expression. It can fill a ProblemDetails object
 * from that information.
 */
public class ExchangeExpressionException extends RuntimeException {

    /**
     * Expression that caused the error
     */
    private final String expression;

    private final Map<String, Object> extensions = new HashMap<>();

    /**
     * Detail field to copy in ProblemDetails
     */
    private String detail;

    /**
     * Weather to include the exception in the ProblemDetails.
     */
    private boolean includeException = true;

    /*
     * Body that cause the error
     */
    private String body;

    /**
     * Whether the message body, not the expression, caused the error.
     */
    private boolean bodyError;

    public ExchangeExpressionException(String expression, String message) {
        super(message);
        this.expression = expression;
    }

    public ExchangeExpressionException(String expression, Throwable cause, String message) {
        super(message, cause);
        this.expression = expression;
    }

    public ExchangeExpressionException(String expression, Throwable cause) {
        super(cause);
        this.expression = expression;
    }

    /**
     * Creates ProblemDetails filled from this exception. A body that is not the document it should be
     * was sent by the client in the request flow (400) and by the backend in the response flow (502).
     * Any other error is one of the expression or the configuration (500).
     */
    public ProblemDetails problemDetails(boolean production, String component, Flow flow) {
        return provideDetails(emptyProblemDetails(production, component, flow));
    }

    /**
     * Like {@link #problemDetails(boolean, String, Flow)}, but not yet filled, for callers that add their own fields
     * before {@link #provideDetails(ProblemDetails)}.
     */
    public ProblemDetails emptyProblemDetails(boolean production, String component, Flow flow) {
        if (!bodyError)
            return internal(production, component);
        if (flow == REQUEST)
            return user(production, component);
        return gateway(production, component).status(502);
    }

    /**
     * @param pd null or ProblemDetails.
     * @return ProblemDetails filled from exception
     */
    public ProblemDetails provideDetails(ProblemDetails pd) {
        if (detail != null) {
            pd.detail(detail);
        } else {
            pd.detail(getMessage());
        }
        pd.internal("expression", expression);
        for (Map.Entry<String, Object> entry : extensions.entrySet()) {
            pd.internal(entry.getKey(), entry.getValue());
        }
        if (body != null)
            pd.internal("body", body.length() > 1024 ? body.substring(0, 1024) : body);
        if (includeException)
            pd.exception(this);
        pd.stacktrace(false);
        return pd;
    }

    public ExchangeExpressionException detail(String detail) {
        this.detail = detail;
        return this;
    }

    public ExchangeExpressionException extension(String key, Object value) {
        extensions.put(key, value);
        return this;
    }

    public ExchangeExpressionException line(String line) {
        extensions.put("line", line);
        return this;
    }

    public ExchangeExpressionException column(String column) {
        extensions.put("column", column);
        return this;
    }

    public ExchangeExpressionException body(String body) {
        this.body = body;
        return this;
    }

    /**
     * Marks the message body, not the expression, as the cause of the error.
     */
    public ExchangeExpressionException bodyError() {
        this.bodyError = true;
        return this;
    }

    public boolean isBodyError() {
        return bodyError;
    }

    /**
     * Call this method if the error is described sufficiently in the message or details that
     * the exception is not needed in the ProblemDetails.
     *
     * @return
     */
    public ExchangeExpressionException excludeException() {
        this.includeException = false;
        return this;
    }

    public Map<String, Object> getExtensions() {
        return extensions;
    }
}
