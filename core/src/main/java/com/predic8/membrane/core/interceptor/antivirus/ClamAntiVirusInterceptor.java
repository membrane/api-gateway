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

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;
import fi.solita.clamav.*;
import org.apache.commons.io.*;
import org.slf4j.*;

import java.io.*;

import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;

/**
 * @description Delegates virus checks to an external Virus Scanner.
 * @topic 6. Security
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
        }catch(Exception ignored){
            // happens only when daemon is not available and then we also want a gateway timeout
        }
        return gatewayTimeout(exc);
    }

    private String getBody(Exchange exc) {
        return exc.getRequest().getBodyAsStringDecoded();
    }

    private Outcome gatewayTimeout(Exchange exc) {
        log.error("Could not reach clamav daemon on {}:{}",host,port );
        internal(router.isProduction(),getDisplayName())
                .title("Virus scanner error!")
                .detail("Could not execute virus scan.")
                .internal("message","Could not reach clamav daemon.")
                .internal("scanner-host", host)
                .internal("scanner-port", port)
                .buildAndSetResponse(exc);
        return RETURN;
    }

    public boolean isNotMalicious(String str) throws IOException {
        try(InputStream input = toInputStream(str)) {
            return ClamAVClient.isCleanReply(client.scan(input));
        }
    }

    private InputStream toInputStream(String str) {
        return IOUtils.toInputStream(str);
    }

    private String getHeaders(Exchange exc) {
         return exc.getRequest().getHeader().toString();
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
