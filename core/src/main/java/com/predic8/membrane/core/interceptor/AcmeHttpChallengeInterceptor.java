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

import java.util.Arrays;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_OCTET_STREAM;
import static java.util.Arrays.stream;

/**
 * @description
 * See the documentation of the <code>&lt;acme /&gt;</code> element for usage details.
 */
@MCElement(name = "acmeHttpChallenge")
public class AcmeHttpChallengeInterceptor extends AbstractInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(AcmeHttpChallengeInterceptor.class);
    public static final String PREFIX = "/.well-known/acme-challenge/";

    private boolean ignorePort;

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        if (exc.getRequest().getUri().startsWith(PREFIX)) {
            String token = exc.getRequest().getUri().substring(PREFIX.length());
            String host = ignorePort
                    ? exc.getRequest().getHeader().getHost().replaceAll(":.*", "")
                    : exc.getRequest().getHeader().getHost();

            for (Rule rule : router.getRules()) {
                if (!(rule instanceof AbstractProxy))
                    continue;
                SSLContext sslInboundContext = ((AbstractProxy) rule).getSslInboundContext();
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

    public boolean isIgnorePort() {
        return ignorePort;
    }

    /**
     * @description For testing purposes only.
     */
    public void setIgnorePort(boolean ignorePort) {
        this.ignorePort = ignorePort;
    }
}
