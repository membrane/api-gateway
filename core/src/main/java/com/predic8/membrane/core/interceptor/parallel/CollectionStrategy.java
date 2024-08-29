package com.predic8.membrane.core.interceptor.parallel;

import com.predic8.membrane.core.exchange.Exchange;

import java.util.ArrayList;
import java.util.List;

public abstract class CollectionStrategy {

    protected static List<Exchange> runningExchanges = new ArrayList<>();
    protected static List<Exchange> completedExchanges = new ArrayList<>();
    protected static Exchange collectedExchange;

    public Exchange handleExchanges(List<Exchange> exchanges) {
        runningExchanges = exchanges;

        for (Exchange exchange : runningExchanges) {
            new Thread(() -> {
                Exchange completedExchange = performCall(exchange);
                completeExchange(completedExchange);
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

    // Ja scheiﬂe nh
    protected abstract Exchange performCall(Exchange exchange);

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
