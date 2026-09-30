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
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

import static com.predic8.membrane.core.exceptions.ProblemDetails.internal;
import static com.predic8.membrane.core.exceptions.ProblemDetails.security;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @description Delegates virus checks to an external Virus Scanner.
 * @topic 3. Security and Validation
 */
@MCElement(name="clamav")
public class ClamAntiVirusInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ClamAntiVirusInterceptor.class);

    private String host = "localhost";
    private String port = "3310";

    ClamAVClient client;

    public ClamAntiVirusInterceptor() {
        name = "clam av";
    }

    @Override
    public String getShortDescription() {
        return "Scans responses for malicious content.";
    }

    @Override
    public void init() {
        super.init();
        client = new ClamAVClient(getHost(), Integer.parseInt(getPort()));
        log.info("Using clamav daemon on [{}:{}]",getHost(),getPort());
    }

    @Override
    public Outcome handleResponse(Exchange exc) {
        try {
            if (isNotMalicious(getHeaders(exc)) && isNotMalicious(getBody(exc)))
                return CONTINUE;
        } catch (Exception e) {
            return scannerFailure(exc, e);
        }
        security(router.getConfiguration().isProduction(), null)
                .addSubType("potentially-harmful-content")
                .title("Request blocked")
                .detail("The request contains potentially harmful content.")
                .buildAndSetResponse(exc);
        return RETURN;
    }

    private String getBody(Exchange exc) {
        return exc.getResponse().getBodyAsStringDecoded();
    }

    private Outcome scannerFailure(Exchange exc, Exception cause) {
        log.error("Could not execute virus scan using clamav daemon on {}:{}", host, port, cause);
        internal(router.getConfiguration().isProduction(), null)
                .title("Request processing failed")
                .detail("Could not execute virus scan.")
                .buildAndSetResponse(exc);
        return RETURN;
    }

    public boolean isNotMalicious(String str) throws IOException {
        try(InputStream input = toInputStream(str)) {
            byte[] reply = client.scan(input);
            if (ClamAVClient.isCleanReply(reply))
                return true;
            String result = new String(reply, UTF_8).trim();
            if (!result.endsWith(" FOUND"))
                throw new IOException("ClamAV scan failed: " + result);
            log.warn("ClamAV detected malicious content: {}", result);
            return false;
        }
    }

    private InputStream toInputStream(String str) {
        return IOUtils.toInputStream(str, UTF_8);
    }

    private String getHeaders(Exchange exc) {
         return exc.getResponse().getHeader().toString();
    }

    public String getHost() {
        return host;
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
