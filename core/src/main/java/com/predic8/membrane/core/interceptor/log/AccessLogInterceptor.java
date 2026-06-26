/* Copyright 2023 predic8 GmbH, www.predic8.com

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

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.log.access.AccessLogInterceptorService;

import java.util.ArrayList;
import java.util.List;

import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;

/**
 * @description Writes one access-log line per completed exchange through a Log4j appender. The line follows the Apache
 * Common Log Format by default; add additionalPatternList entries to append your own SpEL-evaluated fields. Where the
 * line is written and in which format is controlled by the Log4j configuration (log4j2.xml). Typically configured under
 * <code>global</code> so it covers every API. See the examples under examples/logging/access.
 * @topic 4. Monitoring, Logging and Statistics
 * @yaml
 * <pre><code>
 * global:
 *   - accessLog:
 *       additionalPatternList:
 *         - name: forwarded
 *           expression: headers['x-forwarded-for']
 * </code></pre>
 */
@MCElement(name = "accessLog")
public class AccessLogInterceptor extends AbstractInterceptor {
    private List<AdditionalVariable> additionalVariables = new ArrayList<>();
    private String defaultValue = "-";
    private String dateTimePattern = "dd/MM/yyyy:HH:mm:ss Z";
    private boolean excludePayloadSize = false;

    private AccessLogInterceptorService accessLogInterceptorService;

    public AccessLogInterceptor() {
        name = "access log";
    }

    @Override
    public String getShortDescription() {
        return "SpEL expression based exchange data logging through Log4j appender.";
    }

    @Override
    public void init() {
        super.init();

        accessLogInterceptorService = new AccessLogInterceptorService(
                dateTimePattern,
                defaultValue,
                additionalVariables,
                excludePayloadSize,
                router
        );
    }

    @Override
    public Outcome handleResponse(Exchange exc) {
        accessLogInterceptorService.handleAccessLogging(exc);
        return CONTINUE;
    }

    @Override
    public void handleAbort(Exchange exc) {
        accessLogInterceptorService.handleAccessLogging(exc);
    }

    @SuppressWarnings("unused")
    public List<AdditionalVariable> getAdditionalPatternList() {
        return additionalVariables;
    }

    /**
     * @description Extra fields appended to each log line. Each additionalVariable binds a name, referenced in
     * log4j2.xml as %X{name}, to a SpEL expression evaluated against the exchange.
     */
    @SuppressWarnings("unused")
    @MCChildElement
    public void setAdditionalPatternList(List<AdditionalVariable> additionalVariableList) {
        this.additionalVariables = additionalVariableList;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    /**
     * @description Value logged when an exchange property or expression resolves to null.
     * @default -
     * @example N/A
     */
    @MCAttribute
    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    @SuppressWarnings("unused")
    public String getDateTimePattern() {
        return dateTimePattern;
    }

    /**
     * @description Pattern used to format the request timestamp in the log line.
     * @default dd/MM/yyyy:HH:mm:ss Z
     */
    @MCAttribute
    public void setDateTimePattern(String dateTimePattern) {
        this.dateTimePattern = dateTimePattern;
    }

    @SuppressWarnings("unused")
    public boolean isExcludePayloadSize() {
        return excludePayloadSize;
    }

    /**
     * @description Whether to omit the payload size from the log line. Logging the size requires reading the whole
     * body, which disables streaming; set to <code>true</code> to keep streaming.
     * @default false
     */
    @MCAttribute
    public void setExcludePayloadSize(boolean excludePayloadSize) {
        this.excludePayloadSize = excludePayloadSize;
    }
}
