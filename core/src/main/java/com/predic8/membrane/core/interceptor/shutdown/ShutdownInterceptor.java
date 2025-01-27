/* Copyright 2009, 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.shutdown;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;

import static com.predic8.membrane.core.interceptor.Outcome.RETURN;

/**
 * @description Shutdown interceptor.
 * @explanation <p>
 *                  Adds the ability to shutdown main router thread via an HTTP request.
 *                  Don't forget to setup security for this interceptor.
 *              </p>
 *              <p>
 *                  Has undefined behavior when Membrane is <b>not</b> started from command line
 *                  (=via the RouterCLI class, which <tt>service-proxy.bat</tt>/<tt>service-proxy.sh</tt>
 *                  do use).
 *              </p>
 *              <p>
 *                  Only actually terminates the running JVM, when used in the first
 *                  <tt>&lt;router&gt;</tt> bean (which implicitly stands for
 *                  <tt>&lt;router id="router"&gt;</tt>) called "router".
 *              </p>
 * @topic 6. Misc
 */
@MCElement(name="shutdown")
public class ShutdownInterceptor extends AbstractInterceptor {

    public ShutdownInterceptor() {
        name = "shutdown interceptor";
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        if (getRouter().isRunning()) {
            exc.setResponse(Response.ok("Router shutdown procedure was started.").build());
            new Thread(() -> {
                try {
                    Thread.sleep(100); // Wait a moment to finish this request.
                } catch (InterruptedException e) {
                    // DONOTHING
                }
                getRouter().stop(); // stop the router
            }).start();
        } else {
            exc.setResponse(Response.serviceUnavailable("Router is not started.").build());
        }
        return RETURN;
    }

    @Override
    public String getShortDescription() {
        return "Triggers Membrane to initiate a shutdown.";
    }

    @Override
    public String getLongDescription() {
        return "Triggers Membrane to initiate a shutdown.<br/>" +
                "Note that the shutdown is triggered asynchronously after 100ms to allow the shutdown HTTP request to be completed successfully.<br/>" +
                "Also note that the shutdown is not immediate, but pending HTTP Exchanges are given a grace period to complete.";
    }
}
