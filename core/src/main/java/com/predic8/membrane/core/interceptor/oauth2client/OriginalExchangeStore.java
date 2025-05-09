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

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.exchange.snapshots.*;
import com.predic8.membrane.core.interceptor.oauth2client.rf.StateManager;
import com.predic8.membrane.core.interceptor.session.*;

import java.io.*;

import static com.predic8.membrane.core.http.BodyCollectingMessageObserver.Strategy.*;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.*;

public abstract class OriginalExchangeStore {
    public abstract void store(Exchange exchange, Session session, StateManager state, Exchange exchangeToStore) throws IOException;

    protected AbstractExchangeSnapshot getTrimmedAbstractExchangeSnapshot(Exchange exchangeToStore, int limit) throws IOException {
        AbstractExchangeSnapshot excSnapshot = new AbstractExchangeSnapshot(exchangeToStore, REQUEST, null, ERROR, limit);
        // trim the exchange as far as possible to save space
        excSnapshot.getRequest().getHeader().remove("Cookie");
        excSnapshot.setResponse(null);
        FakeProxy p = excSnapshot.getRule();
        return excSnapshot;
    }

    public abstract AbstractExchangeSnapshot reconstruct(Exchange exchange, Session session, StateManager state);

    public abstract void remove(Exchange exc, Session session, StateManager state);

    public abstract void postProcess(Exchange exc);
}
