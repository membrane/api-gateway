/* Copyright 2025 predic8 GmbH, www.predic8.com

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

import com.predic8.membrane.core.http.Message;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.function.Predicate;

import static java.lang.Boolean.FALSE;

public abstract class AbstractExchangeExpression implements ExchangeExpression {

    /**
     * String from which the expression was created
     */
    protected final String expression;

    /**
     * Should only called from subclasses cause ExchangeExpression offers a getInstance method
     * @param expression String with expression like "foo ${property.baz} bar"
     */
    protected AbstractExchangeExpression(String expression) {
        this.expression = expression;
    }

    @Override
    public String getExpression() {
        return expression;
    }

    @Override
    public String toString() {
        return expression;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AbstractExchangeExpression aee)) {
            return false;
        }
        if (!this.getClass().equals(obj.getClass())) {
            return false;
        }
        return expression.equals(aee.expression);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode() + expression.hashCode();
    }

    protected <T> T checkContentTypeAndBody(Message msg, Class<T> type, Predicate<Message> contentTypeOk, String contentTypeLabel, Logger log) {
        // Guard against empty body and other Content-Types
        try {
            if (msg.isBodyEmpty()) {
                log.info("Body is empty. Nothing to evaluate for expression: {}", expression);
                return resultForNoEvaluation(type);
            }
            if (!contentTypeOk.test(msg)) {
                log.info("Content-Type not {}. Nothing to evaluate for expression: {}", contentTypeLabel, expression);
                return resultForNoEvaluation(type);
            }
        } catch (IOException e) {
            log.error("Error checking if body is empty", e);
            return resultForNoEvaluation(type);
        }
        return null;
    }

    private <T> T resultForNoEvaluation(Class<T> type) {
        if (String.class.isAssignableFrom(type)) {
            return type.cast("");
        }
        if (Boolean.class.isAssignableFrom(type)) {
            return type.cast(FALSE);
        }
        return null;
    }
}
