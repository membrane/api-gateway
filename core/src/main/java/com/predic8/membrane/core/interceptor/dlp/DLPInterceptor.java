package com.predic8.membrane.core.interceptor.dlp;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;

/**
 * <dlp fieldsConfig="..." action="report|mask|filter">
 * <fields>
 * <field name="address_.*" action="report"/>
 * <field name="credit.*" action="mask"/> <!-- Set value to **** ->
 * <field name="health" action="filter"/> <!-- Take out field -->
 * <field name="id" action="allow"/>
 * </fields>
 * </dlp>
 * TODO:
 * -
 */
@MCElement(name = "dlp")
public class DLPInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(DLPInterceptor.class);
    private DLP dlp;

    @Override
    public void init() {
        super.init();
        Map<String, String> riskDict = new CsvFieldConfiguration().getFields("dlp-fields.csv");
        dlp = new DLP(riskDict);
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        return handleInternal(exc.getRequest());
    }

    @Override
    public Outcome handleResponse(Exchange exc) {
        return handleInternal(exc.getResponse());
    }

    public Outcome handleInternal(Message msg) {
        if (dlp == null) {
            log.warn("DLP not initialized.");
            return CONTINUE;
        }

        Map<String, Object> riskAnalysis = dlp.analyze(msg);
        log.info("DLP Risk Analysis Result: {}", riskAnalysis);
        return CONTINUE;
    }
}
