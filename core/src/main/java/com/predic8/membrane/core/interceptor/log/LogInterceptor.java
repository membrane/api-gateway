/* Copyright 2009, 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.log;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.lang.*;
import com.predic8.membrane.core.lang.*;
import org.slf4j.*;

import java.io.*;

import static com.predic8.membrane.core.interceptor.Interceptor.Flow.*;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.interceptor.log.LogInterceptor.Level.*;
import static org.slf4j.LoggerFactory.*;

/**
 * @description Logs request and response messages. The messages will appear either on the console or in
 * a log file depending on the log configuration.
 * <p>Typical use cases:
 * <ul>
 *   <li>Debugging APIs during development.</li>
 *   <li>Operational visibility in production (metadata-only, masked values, message, body).</li>
 * </ul>
 * </p>
 * @topic 4. Monitoring, Logging and Statistics
 */
@MCElement(name = "log")
public class LogInterceptor extends AbstractExchangeExpressionInterceptor {

    public enum Level {
        TRACE, DEBUG, INFO, WARN, ERROR, FATAL
    }

    private Level level = INFO;
    private String category = LogInterceptor.class.getName();

    private String label = "";
    private boolean body = true;

    private boolean maskSensitive = true;

    private final SensitiveDataFilter filter = new SensitiveDataFilter();

    public LogInterceptor() {
        name = "log";
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        logMessage(exc, REQUEST);
        return CONTINUE;
    }

    @Override
    public Outcome handleResponse(Exchange exc) {
        logMessage(exc, RESPONSE);
        return CONTINUE;
    }

    @Override
    public void handleAbort(Exchange exc) {
        try {
            writeLog("==== Response(Exchange aborted) %s ===".formatted(label));
            logMessage(exc, ABORT);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void logMessage(Exchange exc, Flow flow) {
        if (getMessage() != null && !getMessage().isEmpty()) {
            try {
                writeLog(exchangeExpression.evaluate(exc, flow, String.class));
            } catch (ExchangeExpressionException e) {
                getLogger(category).warn("Problems evaluating the expression {} . Message: {} Extensions: {}", getMessage(), e.getMessage(), e.getExtensions());
            }
            return;
        }

        writeLog("==== %s %s ===".formatted(flow, label));

        Message msg = exc.getMessage(flow);

        if (msg == null)
            return;
        writeLog(filter.maskStartLine(msg));

        writeLog(dumpHeader(msg));

        try {
            if (!body || msg.isBodyEmpty())
                return;
        } catch (IOException e) {
            writeLog("Error accessing body: " + e.getMessage());
            return;
        }
        writeLog(dumpBody(msg));
    }

    private String dumpHeader(Message msg) {
        return "\nHeaders:\n" + dumpHeaderFields(msg);
    }

    private String dumpHeaderFields(Message msg) {
        return maskSensitive
                ? filter.getMaskedHeader(msg.getHeader()).toString()
                : msg.getHeader().toString();
    }

    private static String dumpBody(Message msg) {
        try {
            return "Body:\n%s\n".formatted(msg.getBodyAsStringDecoded());
        } catch (Exception e) {
            return "Body: [error reading body: %s]".formatted(e.getMessage());
        }
    }

    private void writeLog(String msg) {

        switch (level) {
            case TRACE -> getLogger(category).trace(msg);
            case DEBUG -> getLogger(category).debug(msg);
            case INFO -> getLogger(category).info(msg);
            case WARN -> getLogger(category).warn(msg);
            case ERROR, FATAL -> getLogger(category).error(msg);
        }
    }

    @Override
    public String getShortDescription() {
        return "Logs the " + (body ? "headers of " : "") + "requests and responses" +
                " using Log4J's " + level.toString() + " level.";
    }

    @Override
    public String getDisplayName() {
        return "log";
    }

    /**
     * @description Whether to include message bodies in logs.
     *
     * <p><strong>Warning:</strong> Body logging can expose secrets or personal data. Prefer {@code false}
     * in production.</p>
     * @default true
     */
    @MCAttribute
    public void setBody(boolean body) {
        this.body = body;
    }

    /**
     * Message to write into the log. Can be an expression.
     */
    @MCAttribute
    public void setMessage(String message) {
        expression = message;
    }

    /**
     * @description Whether to mask sensitive header values (e.g., Authorization, Cookies, API keys).
     *
     * <p>When enabled (default), values are replaced by ****.</p>
     *
     *
     */
    @MCAttribute
    public void setMaskSensitive(boolean maskSensitive) {
        this.maskSensitive = maskSensitive;
    }

    /**
     * @description Log level for emitted messages.
     * <p>Values: TRACE, DEBUG, INFO, WARN, ERROR, FATAL</p>
     * @default INFO
     * @example WARN
     */
    @MCAttribute
    public void setLevel(Level level) {
        this.level = level;
    }

    /**
     * @description Short label printed with each log line to distinguish multiple log interceptors.
     *
     * <p>Useful when several APIs share the same category but you want quick visual grouping.</p>
     * @example "After Transformation"
     * @default empty string
     */
    @SuppressWarnings("unused")
    @MCAttribute
    public void setLabel(String label) {
        this.label = label;
    }

    /**
     * @description Logger category to use.
     * <p>Allows routing logs into different appenders/targets via Logback/Log4j configuration.</p>
     * @default Fully qualified class name of {@code LogInterceptor} com.predic8.membrane.core.interceptor.log.LogInterceptor
     */
    @SuppressWarnings("unused")
    @MCAttribute
    public void setCategory(String category) {
        this.category = category;
    }

    /**
     * Deprecated and sunsetted!
     * Do not use this attribute. It is only there for the proxies.xml to be compatible with versions prior to 6.X.X
     * It has no effect at all!
     *
     * @default false
     * @description Ignored. Still there for compatibility.
     */
    @MCAttribute
    public void setHeaderOnly(boolean headerOnly) {
        LoggerFactory.getLogger(this.getClass()).warn("Configuration option `headerOnly` is not supported anymore. Use `body` instead.");
    }

    public boolean isBody() {
        return body;
    }

    public String getMessage() {
        return expression;
    }

    public boolean isMaskSensitive() {
        return maskSensitive;
    }

    public Level getLevel() {
        return level;
    }

    public String getLabel() {
        return label;
    }

    @SuppressWarnings("unused")
    public String getCategory() {
        return category;
    }

    public boolean isHeaderOnly() {
        return false;
    }
}
