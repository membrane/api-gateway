package com.predic8.membrane.core.azure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class AzureDnsApiSimulator {

    private static final Logger log = LoggerFactory.getLogger(AzureDnsApiSimulator.class);

    private final int port;
    private HttpRouter router;

    private Map<String, Object> tableStorage;

    public AzureDnsApiSimulator(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        router = new HttpRouter();
        router.setHotDeploy(false);

        var sp = new ServiceProxy(new ServiceProxyKey(port), "localhost", port);

        sp.getInterceptors().add(new AbstractInterceptor() {
            final ObjectMapper mapper = new ObjectMapper();

            @Override
            public Outcome handleRequest(Exchange exc) throws Exception {
                log.debug("got request {}" + exc.getRequestURI());

                if (List.of("/Tables", "/membrane").stream().anyMatch(uri -> exc.getRequestURI().startsWith(uri))) {

                    var hasNeededHeaders = Arrays.stream(exc.getRequest().getHeader().getAllHeaderFields()).allMatch(headerField ->
                        List.of("Date", "x-ms-version", "DataServiceVersion", "MaxDataServiceVersion", "Authorization")
                                .contains(headerField.getHeaderName())
                    );

                    if (!hasNeededHeaders) {
                        log.debug("headers seem invalid");
                        exc.setResponse(Response.badRequest().build());
                        return Outcome.RETURN;
                    }

                    log.debug("has needed headers and seems to be a valid request");
                    exc.setResponse(Response.statusCode(201)
                            .build()
                    );

                    return Outcome.RETURN;
                }

                exc.setResponse(Response.notFound().build());
                return Outcome.RETURN;
            }
        });

        router.add(sp);
        router.start();
    }

    public void stop() {
        router.stop();
    }
}
