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

import java.io.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.interceptor.log.LogInterceptor.Level.*;
import static org.slf4j.LoggerFactory.*;

/**
 * @description The log feature logs request and response messages. The messages will appear either on the console or in
 * a log file depending on the log configuration.
 * @topic 5. Monitoring, Logging and Statistics
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

    public LogInterceptor() {
        name = "Log";
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        logMessage(exc, Flow.REQUEST);
        return CONTINUE;

    }

    @Override
    public Outcome handleResponse(Exchange exc) {
        logMessage(exc,Flow.RESPONSE);
        return CONTINUE;
    }

    @Override
    public void handleAbort(Exchange exc) {
        try {
            writeLog("==== Response(Exchange aborted) %s ===".formatted(label));
           logMessage(exc, Flow.ABORT);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isBody() {
        return body;
    }

    /**
     * @default true
     * @description To turn off logging of message bodies set this attribute to false
     */
    @MCAttribute
    public void setBody(boolean body) {
        this.body = body;
    }

    public Level getLevel() {
        return level;
    }

    /**
     * @default INFO
     * @description Sets the log level.
     * @example WARN
     */
    @MCAttribute
    public void setLevel(Level level) {
        this.level = level;
    }

    private void logMessage(Exchange exc, Flow flow) {

        if(getMessage() != null) {
            try {
                writeLog(exchangeExpression.evaluate(exc,flow,String.class));
            } catch (ExchangeExpressionException e) {
                getLogger(category).warn("Problems evaluating {} expression: {}",getMessage(),  e.getMessage());
            }
            return;
        }

        writeLog("==== %s %s ===".formatted(flow,label));

        Message msg = exc.getMessage(flow);

        if (msg==null)
            return;
        writeLog(msg.getStartLine());
        writeLog("\nHeaders:\n" + msg.getHeader());

        try {
            if (!body || msg.isBodyEmpty())
                return;
        } catch (IOException e) {
            writeLog("Error accessing body: " + e.getMessage());
            return;
        }

        String mt = msg.getHeader().getContentType();
        if (isJson(mt) ||
            isXML(mt) ||
            isText(mt)) {
            writeLog(dumpBody(msg));
        }
    }
    private static String dumpBody(Message msg) {
        return "Body:\n%s\n".formatted(msg.getBodyAsStringDecoded());
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

    @SuppressWarnings("unused")
    public String getCategory() {
        return category;
    }

    /**
     * @default com.predic8.membrane.core.interceptor.log.LogInterceptor
     * @description Sets the category of the logged message.
     * @example Membrane
     */
    @SuppressWarnings("unused")
    @MCAttribute
    public void setCategory(String category) {
        this.category = category;
    }

    @Override
    public String getShortDescription() {
        return "Logs the " + (body ? "headers of " : "") + "requests and responses" +
               " using Log4J's " + level.toString() + " level.";
    }

    public String getLabel() {
        return label;
    }

    /**
     * @default ""
     * @description Label to find the entry in the log
     * @example "After Transformation"
     */
    @SuppressWarnings("unused")
    @MCAttribute
    public void setLabel(String label) {
        this.label = label;
    }

    @Override
    public String getDisplayName() {
        return "Log";
    }

    /**
     * Message to write into the log. Can be an expression.
     */
    @MCAttribute
    public void setMessage(String message) {
        expression = message;
    }

    public String getMessage() {
        return expression;
    }
}
