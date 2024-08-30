package com.predic8.membrane.core.interceptor.parallel;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.transport.http.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public abstract class CollectionStrategy {

    protected List<Exchange> runningExchanges = new ArrayList<>();
    protected List<Exchange> completedExchanges = new ArrayList<>();
    protected Exchange collectedExchange;

    HttpClient client = new HttpClient();
    Logger log = LoggerFactory.getLogger(CollectionStrategy.class);

    public Exchange handleExchanges(List<Exchange> exchanges) {
        runningExchanges = exchanges;

        for (Exchange exchange : runningExchanges) {
            new Thread(() -> {
                Exchange completedExchange = performCall(exchange);
                synchronized (this) {
                    completeExchange(completedExchange);
                    notifyAll();
                }
            }).start();
        }

        synchronized (this) {
            while (collectedExchange == null) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        return collectedExchange;
    }

    protected Exchange performCall(Exchange exchange) {
        try {
            log.info("Sending request to %s".formatted(exchange.getDestinations().get(0)));
            return client.call(exchange);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Overridden method should call super, then perform resolution strategy.
     * On completion of strategy a single exchange should be assigned to <code>collectedExchange</code>,
     * which will be completed (will actually return to the client).
     */
    public void completeExchange(Exchange exc) {
        completedExchanges.add(exc);
        runningExchanges.remove(exc);
    }

    /**
     * Element which will be used to communicate which strategy implementation should be used.
     * Is used to create fresh instances of the <code>CollectionStrategy</code> every exchange.
     * Implementing classes should be annotated as <code>@MCElement</code> appropriately.
     */
    public interface CollectionStrategyElement {
        CollectionStrategy getNewInstance();
    }
}
