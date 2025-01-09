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

import java.util.*;

public class ExchangeExpressionException extends RuntimeException {

    private final String expression;
    private final Map<String, Object> extensions = new HashMap<>();

    /**
     * User provided message. Overrides the message from the exception
     */
    private String message;

    private boolean statcktrace = true;

    /*
     * Body that cause the error
     */
    private String body;

    public ExchangeExpressionException(String expression, Throwable cause) {
        super(cause);
        this.expression = expression;
    }

    /**
     * @param pd null or ProblemDetails.
     * @return ProblemDetails filled from exception
     */
    public ProblemDetails provideDetails(ProblemDetails pd) {
        if (pd == null)
            pd = ProblemDetails.internal(true);
        if (message != null) {
            pd.extension("message", message);
        } else {
            pd.extension("message", getMessage());
        }
        pd.extension("expression", expression)
            .stacktrace(statcktrace);
        for (Map.Entry<String, Object> entry : extensions.entrySet()) {
            pd.extension(entry.getKey(), entry.getValue());
        }
        if (body != null)
            pd.extension("body", body.length() > 1024 ? body.substring(0, 1024) : body);
        pd.exception(this);
        return pd;
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

    public ExchangeExpressionException message(String message) {
        this.message = message;
        return this;
    }

    public ExchangeExpressionException stacktrace(boolean stacktrace) {
        this.statcktrace = stacktrace;
        return this;
    }

    public ExchangeExpressionException body(String body) {
        this.body = body;
        return this;
    }
}
