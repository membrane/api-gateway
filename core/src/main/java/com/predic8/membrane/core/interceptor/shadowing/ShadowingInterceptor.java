package com.predic8.membrane.core.interceptor.shadowing;

import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.rules.AbstractServiceProxy.Target;
import com.predic8.membrane.core.transport.http.HttpClient;
import com.predic8.membrane.core.util.URIFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static java.util.concurrent.Executors.newCachedThreadPool;

@MCElement(name="shadowing")
public class ShadowingInterceptor extends AbstractInterceptor {

    private static final HttpClient client = new HttpClient();
    private static final Logger log = LoggerFactory.getLogger(ShadowingInterceptor.class);

    private List<Target> targets = new ArrayList<>();

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        // Copy the request headers to ensure we maintain original request details for use in the cloned requests.
        Header copiedHeader = new Header(exc.getRequest().getHeader());
        exc.getRequest().getBody().getObservers().add(new MessageObserver() {
            @Override
            public void bodyRequested(AbstractBody body) {}
            @Override
            public void bodyChunk(Chunk chunk) {}
            @Override
            public void bodyChunk(byte[] buffer, int offset, int length) {}

            @Override
            public void bodyComplete(AbstractBody completeBody) {
                cloneRequestAndSend(completeBody, exc, copiedHeader);
            }
        });
        return CONTINUE;
    }

    @Override
    public String getShortDescription() {
        return "Sends requests to shadow hosts (processed in the background).";
    }

    public void cloneRequestAndSend(AbstractBody completeBody, Exchange mainExchange, Header copiedHeader) {
        ExecutorService executor = newCachedThreadPool();
        for (Target shadowTarget : targets) {
            Exchange newExchange;
            try {
                newExchange = buildExchange(completeBody, mainExchange, shadowTarget, copiedHeader);
            } catch (Exception e) {
                log.error("Error creating request for target {}", shadowTarget, e);
                continue;
            }

            executor.submit(() -> {
                try {
                    Exchange res = performCall(newExchange);
                    if (res.getResponse().getStatusCode() >= 500)
                        log.info("{} returned StatusCode {}", res.getDestinations().get(0), res.getResponse().getStatusCode());
                } catch (Exception e) {
                    log.error("Error performing call for target {}", shadowTarget, e);
                }
            });
        }
    }

    static Exchange buildExchange(AbstractBody completeBody, Exchange mainExchange, Target shadowTarget, Header copiedHeader) throws URISyntaxException, IOException {
        // Build the new Exchange object with the same body, method, and header but targeted at the shadow host.
        return new Request.Builder()
                .body(completeBody.getContent())
                .header(copiedHeader)
                .method(mainExchange.getRequest().getMethod())
                .url(
                    new URIFactory(),
                    getDestFromTarget(
                        shadowTarget,
                        mainExchange.getOriginalRequestUri()
                    )
                )
                .buildExchange();
    }


    static String getDestFromTarget(Target t, String path) {
        return (t.getUrl() != null) ? t.getUrl() : buildTargetUrl(t, path);
    }

    @SuppressWarnings("HttpUrlsUsage")
    private static String buildTargetUrl(Target t, String path) {
        return ((t.getSslParser() != null) ? "https://" : "http://") +
                t.getHost() +
                ":" +
                t.getPort() +
                (path != null ? path : "");
    }

    static Exchange performCall(Exchange exchange) {
        try {
            return client.call(exchange);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Sets the list of shadow hosts to which requests will be cloned and sent.
     * <p>
     * Each target in the list represents a shadow host where the request will be forwarded.
     * These shadow hosts are processed in the background, and if a response from any shadow host
     * contains a 5XX status code, it will be logged.
     * </p>
     *
     * @param targets a list of {@link Target} objects representing the shadow hosts.
     */
    @MCChildElement
    public void setTargets(List<Target> targets) {
        this.targets = targets;
    }

    public List<Target> getTargets() {
        return targets;
    }
}
