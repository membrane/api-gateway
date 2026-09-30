/*
 * Copyright 2016 predic8 GmbH, www.predic8.com
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.interceptor.antivirus;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import fi.solita.clamav.ClamAVClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.predic8.membrane.core.exceptions.ProblemDetails.internal;
import static com.predic8.membrane.core.exceptions.ProblemDetails.security;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.RESPONSE;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static com.predic8.membrane.core.interceptor.antivirus.ScanResult.CLEAN;
import static java.util.Objects.requireNonNull;

/**
 * @description Delegates virus checks to an external Virus Scanner.
 * @topic 3. Security and Validation
 */
@MCElement(name="clamav")
public class ClamAntiVirusInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ClamAntiVirusInterceptor.class);

    private String host = "localhost";
    private String port = "3310";
    private ScanFailureAction onScanFailure = ScanFailureAction.BLOCK;

    public enum ScanFailureAction {
        BLOCK, PASS
    }

    ContentScanner scanner;

    public ClamAntiVirusInterceptor() {
        name = "clam av";
    }

    @Override
    public String getShortDescription() {
        return "Scans requests and responses for malicious content.";
    }

    @Override
    public void init() {
        super.init();
        scanner = new ClamAvScanner(new ClamAVClient(getHost(), Integer.parseInt(getPort())));
        log.info("Using clamav daemon on [{}:{}]",getHost(),getPort());
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        return scan(exc, REQUEST);
    }

    @Override
    public Outcome handleResponse(Exchange exc) {
        return scan(exc, RESPONSE);
    }

    private Outcome scan(Exchange exc, Flow flow) {
        log.debug("Starting antivirus scan for {} flow", flow);
        try {
            if (scanner.scan(getMessage(exc, flow)) == CLEAN) {
                log.debug("Antivirus scan clean for {} flow; continuing processing", flow);
                return CONTINUE;
            }
        } catch (InvalidScanContentException e) {
            log.info("Invalid content in {} flow; blocking regardless of onScanFailure", flow);
            return scannerFailure(exc, e);
        } catch (Exception e) {
            if (onScanFailure == ScanFailureAction.PASS) {
                log.warn("Antivirus scan failed for {} flow; passing message without a completed scan (onScanFailure=pass)", flow, e);
                return CONTINUE;
            }
            log.info("Antivirus scan failed for {} flow; returning an internal error", flow);
            return scannerFailure(exc, e);
        }
        log.debug("Antivirus detected harmful content in {} flow; returning a security error", flow);
        security(router.getConfiguration().isProduction(), null)
                .addSubType("potentially-harmful-content")
                .title(flow == REQUEST ? "Request blocked" : "Response blocked")
                .detail(flow == REQUEST ? "The request contains potentially harmful content."
                        : "The response contains potentially harmful content.")
                .buildAndSetResponse(exc);
        return RETURN;
    }

    private Outcome scannerFailure(Exchange exc, Exception cause) {
        log.warn("Could not execute virus scan using clamav daemon on {}:{}", host, port, cause);
        internal(router.getConfiguration().isProduction(), null)
                .title("Request processing failed")
                .detail("Could not execute virus scan.")
                .buildAndSetResponse(exc);
        return RETURN;
    }

    public String getHost() {
        return host;
    }

    public ScanFailureAction getOnScanFailure() {
        return onScanFailure;
    }

    /**
     * @description Action when scanning cannot complete, such as a scanner connection failure,
     * or timeout. Block returns an error; pass continues with the original message and logs
     * a warning. Confirmed harmful content and invalid multipart content are always blocked.
     * Applies to both requests and responses.
     * @default block
     */
    @MCAttribute
    public void setOnScanFailure(ScanFailureAction onScanFailure) {
        this.onScanFailure = requireNonNull(onScanFailure);
    }

    /**
     * @description the host of the clamav daemon
     * @default localhost
     */
    @MCAttribute
    public void setHost(String host) {
        this.host = host;
    }

    public String getPort() {
        return port;
    }

    /**
     * @description the port of the clamav daemon
     * @default 3310
     */
    @MCAttribute
    public void setPort(String port) {
        this.port = port;
    }
}
