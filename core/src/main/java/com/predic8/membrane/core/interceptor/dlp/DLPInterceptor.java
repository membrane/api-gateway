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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;

@MCElement(name = "dlp")
public class DLPInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(DLPInterceptor.class);

    private DLPAnalyzer dlpAnalyzer;
    private String fieldsConfig;

    private List<Mask> masks = new ArrayList<>();
    private List<Filter> filters = new ArrayList<>();
    private List<Report> reports = new ArrayList<>();

    private final List<DLPAction> actions = new ArrayList<>();

    @Override
    public void init() {
        Map<String, String> config = fieldsConfig != null ?
                new CsvFieldConfiguration().getFields(fieldsConfig) :
                Map.of();

        this.dlpAnalyzer = new DLPAnalyzer(config);

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

    private Outcome handleInternal(Message msg) {
        try {
            String body = msg.getBodyAsStringDecoded();
            RiskReport report = dlpAnalyzer.analyze(msg);
            DLPContext context = new DLPContext(report);
            log.info("DLP Risk Analysis: {}", report.getStructuredReport());

            for (DLPAction action : actions) {
                body = action.apply(body, context);
            }

            msg.setBodyContent(body.getBytes(StandardCharsets.UTF_8));
            return CONTINUE;

        } catch (Exception e) {
            log.error("Exception in DLPInterceptor.handleInternal: ", e);
            return Outcome.ABORT;
        }
    }

    public String getFieldsConfig() {
        return fieldsConfig;
    }

    @MCAttribute
    public void setFieldsConfig(String fieldsConfig) {
        this.fieldsConfig = fieldsConfig;
    }

    public List<Mask> getMasks() {
        return masks;
    }

    @MCChildElement
    public DLPInterceptor setMasks(List<Mask> masks) {
        this.masks = masks;
        return this;
    }

    public List<Filter> getFilters() {
        return filters;
    }

    @MCChildElement(order = 1)
    public DLPInterceptor setFilters(List<Filter> filters) {
        this.filters = filters;
        return this;
    }

    public List<Report> getReports() {
        return reports;
    }

    @MCChildElement(order = 2)
    public void setReports(List<Report> reports) {
        this.reports = reports;
    }
}
