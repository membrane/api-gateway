/* Copyright 2020 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.oauth2client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.exchange.snapshots.AbstractExchangeSnapshot;
import com.predic8.membrane.core.http.BodyCollectingMessageObserver;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.session.Session;

import java.io.IOException;

@MCElement(name = "sessionOriginalExchangeStore")
public class SessionOriginalExchangeStore extends OriginalExchangeStore {
    public static final String ORIGINAL_REQUEST_PREFIX = "_original_request_for_state_";

    private int maxBodySize = 100000;

    private String originalRequestKeyNameInSession(String state) {
        return ORIGINAL_REQUEST_PREFIX + state;
    }

    @Override
    public void store(Exchange exchange, Session session, String state, Exchange exchangeToStore) throws IOException {
        try {
            session.put(originalRequestKeyNameInSession(state),new ObjectMapper().writeValueAsString(getTrimmedAbstractExchangeSnapshot(exchangeToStore, maxBodySize)));
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

    public int getMaxBodySize() {
        return maxBodySize;
    }

    @MCAttribute
    public void setMaxBodySize(int maxBodySize) {
        this.maxBodySize = maxBodySize;
    }
}
