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

package com.predic8.membrane.core.interceptor;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;

import java.io.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.interceptor.LogInterceptor.Level.INFO;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static org.slf4j.LoggerFactory.*;

/**
 * @description The log feature logs request and response messages. The messages will appear either on the console or in
 * a log file depending on the log configuration.
 * @topic 5. Monitoring, Logging and Statistics
 */
@MCElement(name = "log")
public class LogInterceptor extends AbstractInterceptor {

    public enum Level {
        TRACE, DEBUG, INFO, WARN, ERROR, FATAL
    }

    private boolean body = true;
    private String category = LogInterceptor.class.getName();
    private Level level = INFO;
    private String label;
    private boolean properties;

    public LogInterceptor() {
        name = "Log";
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        log("==== Request %s ===".formatted(label));
        logMessage(exc, exc.getRequest());
        return CONTINUE;
    }

    @Override
    public Outcome handleResponse(Exchange exc) {
        log("==== Response %s ===".formatted(label));
        logMessage(exc,exc.getResponse());
        return CONTINUE;
    }

    @Override
    public void handleAbort(Exchange exc) {
        try {
            log("==== Response(Exchange aborted) %s ===".formatted(label));
            logMessage(exc, exc.getResponse());
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

    private void logMessage(Exchange exc, Message msg) {

        log(dumpProperties(exc));

        if (msg==null)
            return;
        log(msg.getStartLine());
        log("\nHeaders:\n" + msg.getHeader());

        try {
            if (!body || msg.isBodyEmpty())
                return;
        } catch (IOException e) {
            log("Error accessing body: " + e.getMessage());
            return;
        }

        String mt = msg.getHeader().getContentType();
        if (isJson(mt) ||
            isXML(mt) ||
            isText(mt)) {
            log(dumpBody(msg));
        }
    }

    private String dumpProperties(Exchange exc) {
        return "Properties: " + exc.getProperties();
    }

    private static String dumpBody(Message msg) {
        return "Body:\n{%s}\n".formatted(msg.getBodyAsStringDecoded());
    }

    private void log(String msg) {
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
     * @default com.predic8.membrane.core.interceptor.LogInterceptor
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

    public boolean getProperties() {
        return properties;
    }

    /**
     * @default
     * @description
     * @example
     */
    @SuppressWarnings("unused")
    @MCAttribute
    public void setProperties(boolean properties) {
        this.properties = properties;
    }
}
