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

/**
 * @description Shutdown interceptor.
 * @explanation Adds the ability to shutdown main router thread via an HTTP request.
 *              Don't forget to setup security for this interceptor.
 * @topic 4. Interceptors/Features
 */
@MCElement(name="shutdown")
public class ShutdownInterceptor extends AbstractInterceptor {
    
    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        exc.setResponse(Response.ok().contentType("text/plain").body("OK").build());
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(100); // Wait a moment to finish this request.
                } catch (InterruptedException e) {
                    // DONOTHING
                }
                getRouter().stop(); // stop the router
            }
        }).start();
        return Outcome.RETURN;
    }

}
