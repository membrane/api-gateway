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

import java.util.*;

import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;

/**
 * @description Writes exchange metrics into a Log4j appender
 * @explanation Defaults to Apache Common Log pattern
 */
@MCElement(name = "accessLog")
public class AccessLogInterceptor extends AbstractInterceptor {
    private List<AdditionalVariable> additionalVariables = new ArrayList<>();
    private String defaultValue = "-";
    private String dateTimePattern = "dd/MM/yyyy:HH:mm:ss Z";
    private boolean excludePayloadSize = false;

    private AccessLogInterceptorService accessLogInterceptorService;

    @Override
    public void init() throws Exception {
        super.init();

        accessLogInterceptorService = new AccessLogInterceptorService(
                dateTimePattern,
                defaultValue,
                additionalVariables,
                excludePayloadSize
        );
    }

    @Override
    public Outcome handleResponse(Exchange exc) throws Exception {
        accessLogInterceptorService.handleAccessLogging(exc);
        return CONTINUE;
    }

    @SuppressWarnings("unused")
    public List<AdditionalVariable> getAdditionalPatternList() {
        return additionalVariables;
    }

    @SuppressWarnings("unused")
    @MCChildElement
    public void setAdditionalPatternList(List<AdditionalVariable> additionalVariableList) {
        this.additionalVariables = additionalVariableList;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    /**
     * @description - Provide a default value if the exchange property could not be found, defaults to "-"
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
     * @description - Provide a datetime pattern, defaults to "dd/MM/yyyy:HH:mm:ss Z"
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
     * @description - Reading the payload size would disable "Streaming", defaults to false
     */
    @MCAttribute
    public void setExcludePayloadSize(boolean excludePayloadSize) {
        this.excludePayloadSize = excludePayloadSize;
    }
}
