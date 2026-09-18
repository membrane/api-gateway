package com.predic8.membrane.load;

import com.predic8.membrane.core.exchangestore.ForgetfulExchangeStore;
import com.predic8.membrane.core.interceptor.flow.ReturnInterceptor;
import com.predic8.membrane.core.openapi.serviceproxy.APIProxy;
import com.predic8.membrane.core.openapi.serviceproxy.APIProxyKey;
import com.predic8.membrane.core.router.DefaultRouter;
import com.predic8.membrane.core.transport.http.HttpTransport;

/**
 * Standalone backend role for the 3-VM Azure performance test (see ../README.md). Adapted from
 * the backend half of LoadTester.startMembrane() so it can run on its own VM instead of
 * in-process with the client and gateway. Simply echoes every request back with a 200 -- it
 * exists to give the gateway a real network hop to forward to, not to model business logic.
 * <p>
 * Run: java -cp "membrane-api-gateway-VERSION/lib/*:classes" com.predic8.membrane.load.LoadTesterBackend [port]
 * Port defaults to env var BACKEND_PORT, then 2010.
 */
public class LoadTesterBackend {

    public static void main(String[] args) throws Exception {
        int port = args.length > 0
                ? Integer.parseInt(args[0])
                : Integer.parseInt(System.getenv().getOrDefault("BACKEND_PORT", "2010"));

        var r = new DefaultRouter();
        r.setExchangeStore(new ForgetfulExchangeStore());
        // Default backlog is 50 (HttpTransport.backlog); far too small for the concurrency levels
        // used in this test and causes SYN-queue overflow/retransmits under load.
        // getTransport() is null until init(), so construct and set it explicitly up front.
        var transport = new HttpTransport();
        transport.setBacklog(1024);
        r.setTransport(transport);

        var backend = new APIProxy();
        backend.setKey(new APIProxyKey(port));
        backend.getFlow().add(new ReturnInterceptor());
        r.add(backend);

        r.start();
        System.out.println("LoadTesterBackend listening on 0.0.0.0:" + port);

        Thread.currentThread().join();
    }
}
