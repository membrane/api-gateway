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
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import fi.solita.clamav.ClamAVClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

@MCElement(name="clamav")
public class ClamAntiVirusInterceptor extends AbstractInterceptor {

    private static Logger log = LoggerFactory.getLogger(ClamAntiVirusInterceptor.class);

    private String host = "localhost";
    private String port = "3310";

    ClamAVClient client;

    @Override
    public void init(Router router) throws Exception {
        super.init(router);
        client = new ClamAVClient(getHost(), Integer.parseInt(getPort()));
        log.info("Using clamav daemon on [" + getHost() + ":" + getPort() + "]");
    }

    @Override
    public Outcome handleResponse(Exchange exc) throws Exception {
        try {
            if (isNotMalicious(getHeaders(exc)) && isNotMalicious(getBody(exc)))
                return Outcome.CONTINUE;
        }catch(Exception ignored){
            // happens only when daemon is not available and then we also want a gateway timeout
        }
        return gatewayTimeout(exc);
    }

    private String getBody(Exchange exc) {
        return exc.getRequest().getBodyAsStringDecoded();
    }

    private Outcome gatewayTimeout(Exchange exc) {
        exc.setResponse(Response.badGateway("Could not reach clamav daemon on [" + getHost() + ":" + getPort() + "]").build());
        return Outcome.RETURN;
    }

    public boolean isNotMalicious(String str) throws IOException {
        InputStream input = toInputStream(str);
        try{
            return ClamAVClient.isCleanReply(client.scan(input));
        }finally{
            if(input != null)
                input.close();
        }
    }

    private InputStream toInputStream(String str) {
        return org.apache.commons.io.IOUtils.toInputStream(str);
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
