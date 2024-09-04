package com.predic8.membrane.core.interceptor;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.AbstractBody;
import com.predic8.membrane.core.http.Chunk;
import com.predic8.membrane.core.http.MessageObserver;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.transport.http.HttpClient;

@MCElement(name="shadow")
public class DemoShadowingInterceptor extends AbstractInterceptor {

    static HttpClient client = new HttpClient();

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        exc.getRequest().getBody().getObservers().add(new MessageObserver() {
            @Override
            public void bodyRequested(AbstractBody body) {
            }

            @Override
            public void bodyChunk(Chunk chunk) {

            }

            @Override
            public void bodyChunk(byte[] buffer, int offset, int length) {

            }

            @Override
            public void bodyComplete(AbstractBody body) {
                cloneRequestAndSend(body);
            }
        });
        return Outcome.CONTINUE;
    }

    static void cloneRequestAndSend(AbstractBody body) {
        try {
            new Thread(() -> performCall(new Request.Builder().body(body.getContent()).get("http://localhost:3000").buildExchange())).start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void performCall(Exchange exchange) {
        try {
            client.call(exchange);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
