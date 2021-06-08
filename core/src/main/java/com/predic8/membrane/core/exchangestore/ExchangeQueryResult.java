package com.predic8.membrane.core.exchangestore;

import com.predic8.membrane.core.exchange.AbstractExchange;

import java.util.List;

public class ExchangeQueryResult {
    List<AbstractExchange> exchanges;
    int count;
    long lastModified;

    public ExchangeQueryResult(List<AbstractExchange> exchanges, int count, long lastModified) {
        this.exchanges = exchanges;
        this.count = count;
        this.lastModified = lastModified;
    }

    public List<AbstractExchange> getExchanges() {
        return exchanges;
    }

    public int getCount() {
        return count;
    }

    public long getLastModified() {
        return lastModified;
    }
}
