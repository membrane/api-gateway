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

    private void addObservers(AbstractExchange exc, AbstractExchangeSnapshot excCopy, Consumer<AbstractExchangeSnapshot> callback) {
        MessageObserver obs = new MessageObserver() {
            @Override
            public void bodyRequested(AbstractBody body) {

            }

            @Override
            public void bodyComplete(AbstractBody body) {
                update(callback, excCopy, exc);
            }
        };

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

    public void update(Consumer<AbstractExchangeSnapshot> callback, AbstractExchangeSnapshot excCopy, AbstractExchange exc) {
        try {
            excCopy = excCopy.updateFrom(exc);
            if(callback != null)
                callback.call(excCopy);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
