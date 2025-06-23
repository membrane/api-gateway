package com.predic8.membrane.core.interceptor.dlp;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
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

@MCElement(name = "dlp")
public class DLPInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(DLPInterceptor.class);
    private DLP dlp;

    @Override
    public void init() {
        super.init();
        Map<String, String> riskDict = new HashMap<>();
        load("dlp-fields.csv", riskDict);
        dlp = new DLP(riskDict);
    }


    @Override
    public Outcome handleRequest(Exchange exc) {
        if (dlp == null) {
            log.warn("DLP not initialized.");
            return Outcome.CONTINUE;
        }

        Map<String, Object> riskAnalysis = dlp.analyze(exc.getRequest());
        log.info("DLP Risk Analysis Result: {}", riskAnalysis);
        return Outcome.CONTINUE;
    }

    public void load(String fileName, Map<String, String> riskDict) {
        try (InputStream inputStream = DLPInterceptor.class.getClassLoader().getResourceAsStream(fileName)) {

            if (inputStream == null) {
                log.error("Could not find file: {}", fileName);
                throw new NullPointerException("InputStream is null. File not found: " + fileName);
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            boolean isHeader = true;

            while ((line = reader.readLine()) != null) {
                if (isHeader) {
                    isHeader = false;
                    continue;
                }

                String[] parts = line.split(",", -1);
                if (parts.length >= 3) {
                    String field = parts[0].trim().toLowerCase();
                    String riskLevel = parts[2].trim();
                    riskDict.put(field, riskLevel);
                } else {
                    log.warn("Invalid CSV line: {}", line);
                }
            }

        } catch (IOException e) {
            throw new RuntimeException("Failed to load risk data from " + fileName, e);
        }
    }
}
