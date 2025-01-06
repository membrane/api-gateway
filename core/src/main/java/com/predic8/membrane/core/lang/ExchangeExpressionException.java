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

    private String expression;
    private String localizedMessage;
    private Map<String,Object> extensions = new HashMap<>();

    public ExchangeExpressionException( String expression, Throwable cause) {
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
        pd.extension("message", getMessage())
                .extension("expression", expression)
                .extension("localizedMessage", localizedMessage);
        for (Map.Entry<String,Object> entry : extensions.entrySet()) {
            pd.extension(entry.getKey(), entry.getValue());
        }
        pd.exception(this);
        return pd;
    }

    public ExchangeExpressionException localizedMessage(String localizedMessage) {
        this.localizedMessage = localizedMessage;
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
}
