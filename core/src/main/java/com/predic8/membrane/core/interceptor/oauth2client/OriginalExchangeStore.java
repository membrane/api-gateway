package com.predic8.membrane.core.interceptor.oauth2client;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.exchange.snapshots.AbstractExchangeSnapshot;
import com.predic8.membrane.core.interceptor.session.Session;

public abstract class OriginalExchangeStore {
    public abstract void store(Exchange exchange, Session session, String state, Exchange exchangeToStore);

    public abstract AbstractExchangeSnapshot reconstruct(Exchange exchange, Session session, String state);

    public abstract void remove(Exchange exc, Session session, String state);

    public abstract void postProcess(Exchange exc);
}
