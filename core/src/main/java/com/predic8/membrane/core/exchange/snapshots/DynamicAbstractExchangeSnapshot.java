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
import com.predic8.membrane.core.http.BodyCollectingMessageObserver;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.model.AbstractExchangeViewerListener;
import com.predic8.membrane.core.util.functionalInterfaces.ExceptionThrowingConsumer;
import groovy.lang.Tuple2;

import java.io.IOException;
import java.util.stream.Stream;

public class DynamicAbstractExchangeSnapshot extends AbstractExchangeSnapshot{

    /**
     * @param exc the exchange to snapshot
     * @param flow what to copy from the exchange besides general properties
     * @param bodyCopiedCallback will be called once the {@code flow}'s body has been filled. if null, the body stream will be read
     *                 into memory immediately.
     * @param strategy how to handle body lengths exceeding the {@code limit}.
     * @param limit maximum length of the body.
     */
    public DynamicAbstractExchangeSnapshot(AbstractExchange exc, Interceptor.Flow flow, ExceptionThrowingConsumer<AbstractExchangeSnapshot> bodyCopiedCallback, BodyCollectingMessageObserver.Strategy strategy, long limit) throws IOException {
        super(exc, flow, bodyCopiedCallback, strategy, limit);
        addObservers(exc,this, bodyCopiedCallback, flow);
    }

    /**
     * called by JSON deserializer
     */
    public DynamicAbstractExchangeSnapshot() {
    }


    public static void addObservers(AbstractExchange exc, AbstractExchangeSnapshot excCopy, ExceptionThrowingConsumer<AbstractExchangeSnapshot> callback, Interceptor.Flow flow) {
        exc.addExchangeViewerListener(new AbstractExchangeViewerListener() {
            @Override
            public void addResponse(Response response) {
                // do nothing
            }

            @Override
            public void setExchangeFinished() {
                update(callback, excCopy, exc, null);
            }
        });

        update(callback,excCopy,exc,flow);
    }

    public static void update(ExceptionThrowingConsumer<AbstractExchangeSnapshot> callback, AbstractExchangeSnapshot excCopy, AbstractExchange exc, Interceptor.Flow flow) {
        try {
            excCopy = excCopy.updateFrom(exc, flow);

            if (callback != null) {
                callback.accept(excCopy);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
