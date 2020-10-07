/* Copyright 2018 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.exchange.snapshots;

import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.membrane.core.http.AbstractBody;
import com.predic8.membrane.core.http.MessageObserver;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.model.AbstractExchangeViewerListener;
import com.predic8.membrane.core.util.functionalInterfaces.Consumer;

import java.util.stream.Stream;

public class DynamicAbstractExchangeSnapshot extends AbstractExchangeSnapshot{

    public DynamicAbstractExchangeSnapshot() {
        this(null,null);
    }

    public DynamicAbstractExchangeSnapshot(AbstractExchange exc, Consumer<AbstractExchangeSnapshot> updateCallback) {
        super(exc);
        addObservers(exc,this,updateCallback);
    }

    public static void addObservers(AbstractExchange exc, AbstractExchangeSnapshot excCopy, Consumer<AbstractExchangeSnapshot> callback) {
        MessageObserver obs = new UpdateExchangeCopyObserver(callback, excCopy, exc);

        exc.addExchangeViewerListener(new AbstractExchangeViewerListener() {
            @Override
            public void addResponse(Response response) {
                response.addObserver(obs);
            }

            @Override
            public void setExchangeFinished() {
                update(callback, excCopy, exc);
            }
        });

        Stream.of(exc.getRequest(),exc.getResponse()).forEach(msg -> {
            if(msg == null)
                return;
            if(msg.containsObserver(obs))
                return;
            msg.addObserver(obs);
        });

        update(callback,excCopy,exc);
    }

    public static void update(Consumer<AbstractExchangeSnapshot> callback, AbstractExchangeSnapshot excCopy, AbstractExchange exc) {
        try {
            excCopy = excCopy.updateFrom(exc);
            if(callback != null)
                callback.call(excCopy);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static class UpdateExchangeCopyObserver implements MessageObserver {
        private final Consumer<AbstractExchangeSnapshot> callback;
        private final AbstractExchangeSnapshot excCopy;
        private final AbstractExchange exc;

        public UpdateExchangeCopyObserver(Consumer<AbstractExchangeSnapshot> callback, AbstractExchangeSnapshot excCopy, AbstractExchange exc) {
            this.callback = callback;
            this.excCopy = excCopy;
            this.exc = exc;
        }

        @Override
        public void bodyRequested(AbstractBody body) {

        }

        @Override
        public void bodyComplete(AbstractBody body) {
            update(callback, excCopy, exc);
        }
    }
}
