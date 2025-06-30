package com.predic8.membrane.core.interceptor.dlp;

import com.predic8.membrane.core.http.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

public class ReportField implements FieldActionStrategy {
    private static final Logger log = LoggerFactory.getLogger(ReportField.class);

    @Override
    public void apply(Message msg, Pattern pattern) {
        log.debug("Report-only action for pattern '{}'", pattern);
    }
}
