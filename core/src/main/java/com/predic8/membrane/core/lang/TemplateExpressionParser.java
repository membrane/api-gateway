/* Copyright 2026 predic8 GmbH, www.predic8.com

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

import com.predic8.membrane.core.interceptor.*;
import org.slf4j.*;

import java.util.*;

import static com.predic8.membrane.core.lang.ExchangeExpression.*;

public class TemplateExpressionParser {

    private static final Logger log = LoggerFactory.getLogger(TemplateExpressionParser.class);

    public static List<TemplateExchangeExpression.Token> parseTokens(Interceptor interceptor, ExchangeExpression.Language language, String expression) {
        log.debug("Parsing: {}", expression);

        List<TemplateExchangeExpression.Token> tokens = new ArrayList<>();

        int i = 0;
        int textStart = 0;

        while (i < expression.length()) {
            int start = expression.indexOf("${", i);
            if (start < 0) break;

            // emit preceding text
            if (start > textStart) {
                tokens.add(new TemplateExchangeExpression.Text(expression.substring(textStart, start)));
            }

            int end = findEndOfTemplateExpr(expression, start + 2); // index of matching '}' (inclusive)
            if (end < 0) {
                // Unmatched ${ ... -> treat as text (or throw, if you prefer)
                tokens.add(new TemplateExchangeExpression.Text(expression.substring(start)));
                log.debug("Tokens: {}", tokens);
                throw new ExchangeExpressionException(expression, "Unclosed ${...} in: " + expression);
            }

            String inner = expression.substring(start + 2, end); // without ${ and }
            tokens.add(new TemplateExchangeExpression.Expression(expression(interceptor, language, inner)));

            i = end + 1;
            textStart = i;
        }

        // trailing text
        if (textStart < expression.length()) {
            tokens.add(new TemplateExchangeExpression.Text(expression.substring(textStart)));
        }

        log.debug("Tokens: {}", tokens);
        return tokens;
    }

    /**
     * Finds the matching closing '}' for a template expression that starts right after "${".
     * Supports nested braces inside the expression (e.g. new String[]{...}).
     * Also ignores braces inside single/double-quoted string literals.
     *
     * @param s    full input
     * @param from index right after "${"
     * @return index of the matching '}' (inclusive) or -1 if not found
     */
    private static int findEndOfTemplateExpr(String s, int from) {
        int braceDepth = 0;

        boolean inSingle = false;
        boolean inDouble = false;

        for (int i = from; i < s.length(); i++) {
            char c = s.charAt(i);

            // Handle string literals to avoid counting braces inside quotes
            if (inSingle) {
                if (c == '\'') {
                    // SpEL style escaping: '' inside single-quoted string
                    if (i + 1 < s.length() && s.charAt(i + 1) == '\'') {
                        i++; // skip the escaped quote
                    } else {
                        inSingle = false;
                    }
                }
                continue;
            }
            if (inDouble) {
                if (c == '\\') {
                    i++; // skip escaped char
                    continue;
                }
                if (c == '"') {
                    inDouble = false;
                }
                continue;
            }

            if (c == '\'') {
                inSingle = true;
                continue;
            }
            if (c == '"') {
                inDouble = true;
                continue;
            }

            if (c == '{') {
                braceDepth++;
                continue;
            }

            if (c == '}') {
                if (braceDepth == 0) {
                    return i; // matching end of ${...}
                }
                braceDepth--;
            }
        }

        return -1;
    }

}