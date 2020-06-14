package com.predic8.membrane.core.interceptor.oauth2client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.exchange.snapshots.AbstractExchangeSnapshot;
import com.predic8.membrane.core.interceptor.session.Session;

@MCElement(name = "sessionOriginalExchangeStore")
public class SessionOriginalExchangeStore extends OriginalExchangeStore {
    public static final String ORIGINAL_REQUEST_PREFIX = "_original_request_for_state_";

    private String originalRequestKeyNameInSession(String state) {
        return ORIGINAL_REQUEST_PREFIX + state;
    }

    @Override
    public void store(Exchange exchange, Session session, String state, Exchange exchangeToStore) {
        AbstractExchangeSnapshot excSnapshot = new AbstractExchangeSnapshot(exchangeToStore);
        // trim the exchange as far as possible to save space
        excSnapshot.getRequest().getHeader().remove("Cookie");
        excSnapshot.setResponse(null);

        try {
            session.put(originalRequestKeyNameInSession(state),new ObjectMapper().writeValueAsString(excSnapshot));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public AbstractExchangeSnapshot reconstruct(Exchange exchange, Session session, String state) {
        try {
            return new ObjectMapper().readValue(session.get(originalRequestKeyNameInSession(state)).toString(),AbstractExchangeSnapshot.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void remove(Exchange exc, Session session, String state) {
        session.remove(originalRequestKeyNameInSession(state));
    }

    @Override
    public void postProcess(Exchange exc) {

    }
}
