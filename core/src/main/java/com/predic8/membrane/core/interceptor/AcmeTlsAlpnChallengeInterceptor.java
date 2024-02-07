/* Copyright 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.rules.AbstractProxy;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.transport.ssl.AcmeSSLContext;
import com.predic8.membrane.core.transport.ssl.SSLContext;
import com.predic8.membrane.core.transport.ssl.acme.AcmeClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_OCTET_STREAM;
import static java.util.Arrays.stream;

/**
 * @description
 * See the documentation of the <code>&lt;acme /&gt;</code> element for usage details.
 */
@MCElement(name = "acmeTlsAlpnChallenge")
public class AcmeTlsAlpnChallengeInterceptor extends AbstractInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(AcmeHttpChallengeInterceptor.class);

    public AcmeTlsAlpnChallengeInterceptor() {
        name = "ACME TLS ALPN 01 Challenge";
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        if (exc.getRequest().getUri().startsWith(PREFIX)) {
            String token = exc.getRequest().getUri().substring(PREFIX.length());

            for (Rule rule : router.getRules()) {
                if (!(rule instanceof AbstractProxy))
                    continue;
                SSLContext sslInboundContext = rule.getSslInboundContext();
                if (!(sslInboundContext instanceof AcmeSSLContext))
                    continue;
                AcmeSSLContext acmeSSLContext = (AcmeSSLContext) sslInboundContext;

                if (stream(acmeSSLContext.getHosts()).noneMatch(h -> h.equals(host)))
                    continue;

                AcmeClient acmeClient = acmeSSLContext.getClient();

                String correctToken = acmeClient.getToken(host);

                if (correctToken != null && correctToken.equals(token)) {
                    String keyAuth = token + "." + acmeClient.getThumbprint();

                    exc.setResponse(Response.ok()
                            .header("Content-Type", APPLICATION_OCTET_STREAM)
                            .body(keyAuth).build());
                    return Outcome.RETURN;
                }
            }

            LOG.warn("Returning 404 in response to ACME challenge token " + token);
            exc.setResponse(Response.notFound().build());
            return Outcome.RETURN;
        }
        return super.handleRequest(exc);
    }

    @Override
    public String getShortDescription() {
        return "Responds to HTTP requests starting with <font style=\"font-family: monospace\">/.well-known/acme-challenge/</font>.";
    }

    @Override
    public String getLongDescription() {
        return "<div>Responds to HTTP requests starting with <font style=\"font-family: monospace\">/.well-known/acme-" +
                "challenge/</font>.<br/>" +
                "See ACME (RFC 8555, also known as \"Let's Encrypt\") <a href=\"https://www.rfc-editor.org/rfc/rfc8555." +
                "html#section-8.3\">HTTP Challenges</a> for details.</div>";
    }

}