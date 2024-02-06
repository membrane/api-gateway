package com.predic8.membrane.core.interceptor.oauth2client.rf;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.LogInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogHelper {
    private static final Logger log = LoggerFactory.getLogger(LogHelper.class);
    private LogInterceptor logi;

    public LogHelper() {
        if (log.isDebugEnabled()) {
            logi = new LogInterceptor();
            logi.setHeaderOnly(false);
        }
    }

    public void handleRequest(Exchange e) throws Exception {
        if (log.isDebugEnabled()) {
            logi.handleRequest(e);
        }
    }

    public void handleResponse(Exchange e) throws Exception {
        if (log.isDebugEnabled()) {
            logi.handleResponse(e);
        }
    }

}
