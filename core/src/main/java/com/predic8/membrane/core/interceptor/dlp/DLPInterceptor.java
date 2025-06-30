package com.predic8.membrane.core.interceptor.dlp;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;

@MCElement(name = "dlp")
public class DLPInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(DLPInterceptor.class);
    private DLPAnalyzer dlpAnalyzer;
    private String fieldsConfig;
    private String action = "report";
    private Fields fields;
    private Filter filter;

    @Override
    public void init() {
        if (fieldsConfig != null) {
            dlpAnalyzer = new DLPAnalyzer(new CsvFieldConfiguration().getFields(fieldsConfig));
        } else {
            dlpAnalyzer = new DLPAnalyzer(java.util.Map.of());
        }
        super.init();
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

        RiskReport report = dlpAnalyzer.analyze(msg);
        log.info("DLP Risk Analysis: {}", report.getLogReport());
        msg.setBodyContent(filter.apply(msg.getBodyAsStringDecoded()).getBytes(StandardCharsets.UTF_8));
        return CONTINUE;
    }

    public String getFieldsConfig() {
        return fieldsConfig;
    }

    @MCAttribute
    public void setFieldsConfig(String fieldsConfig) {
        this.fieldsConfig = fieldsConfig;
    }

    public String getAction() {
        return action;
    }

    @MCAttribute
    public void setAction(String action) {
        this.action = action;
    }

    public Fields getFields() {
        return fields;
    }

    @MCChildElement
    public void setFields(Fields fields) {
        this.fields = fields;
    }

    public Filter getFilter() {
        return filter;
    }

    @MCChildElement(order = 1)
    public void setFilter(Filter filter) {
        this.filter = filter;
    }

}
