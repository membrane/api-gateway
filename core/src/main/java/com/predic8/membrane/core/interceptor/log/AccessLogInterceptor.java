package com.predic8.membrane.core.interceptor.log;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;

import java.util.*;

/**
 * @description Writes exchange metrics into a Log4j appender
 * @explanation Defaults to Apache Common Log pattern
 */
@MCElement(name = "accessLog")
public class AccessLogInterceptor extends AbstractInterceptor {
    private List<AdditionalPattern> additionalPatternList = new ArrayList<>();
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
                additionalPatternList,
                excludePayloadSize
        );
    }

    @Override
    public Outcome handleResponse(Exchange exc) throws Exception {
        accessLogInterceptorService.handleAccessLogging(exc);
        return super.handleResponse(exc);
    }

    public List<AdditionalPattern> getAdditionalPatternList() {
        return additionalPatternList;
    }

    @MCChildElement
    public void setAdditionalPatternList(List<AdditionalPattern> additionalPatternList) {
        this.additionalPatternList = additionalPatternList;
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
